/* Compiled with actual pinned xllm headers, core/parts, reviewed serializer,
 * private client cache wrapper and XRT JSON/base64. No model/network runtime. */
#include <assert.h>

static size_t alloc_step, fail_step, live_blocks;
static void* qa_alloc(size_t n) {
    void* p;
    if (++alloc_step == fail_step) return NULL;
    p = malloc(n); if (p) ++live_blocks; return p;
}
static void* qa_realloc(void* old, size_t n) {
    void* p;
    if (++alloc_step == fail_step) return NULL;
    p = realloc(old, n); if (p && !old) ++live_blocks; return p;
}
static void qa_free(void* p) { if (p) { assert(live_blocks); --live_blocks; free(p); } }
static void clean(xllm_client* client) {
    xllm__free(client->sPrefixCacheInner);
    client->sPrefixCacheInner = NULL;
    assert(live_blocks == 0);
}
static void reset_cache(xllm_client* client) {
    clean(client);
    client->pPrefixCacheOwner = NULL;
    client->iPrefixCacheMessages = client->iPrefixCacheLen = client->iPrefixCacheCap = 0;
}
static void same_body(xllm_client* client, xllm_request* request, const char* expected) {
    xllm_error error = {0};
    char* body = xllm__client_serialize_body(client, request, &error);
    assert(body && strcmp(body, expected) == 0);
    xllm__free(body);
}
int main(void) {
    static const unsigned char png[] = {137,80,78,71,13,10,26,10,0,0,0,0};
    static const char id[] = "one\"\n\xF0\x9F\xA6\x89";
    xllm_part image_only = {.eKind=XLLM_PART_IMAGE, .sSourceUrl="https://fixture.invalid/two.png", .sDetail="low"};
    xllm_part mixed[] = {{.eKind=XLLM_PART_TEXT, .sText="caption A\n\""},
        {.eKind=XLLM_PART_IMAGE, .pData=png, .iDataSize=sizeof(png), .sMediaType="image/png"},
        {.eKind=XLLM_PART_TEXT, .sText="caption B"},
        {.eKind=XLLM_PART_IMAGE, .sSourceUrl="https://fixture.invalid/one.png?q=\"", .sDetail="high"},
        {.eKind=XLLM_PART_REASONING, .sText="private reasoning"}};
    xllm_tool_call calls[] = {{.sId=id,.sName="capture",.sArgumentsJson="{}"},
        {.sId="two",.sName="capture",.sArgumentsJson="{}"}, {.sId="three",.sName="text",.sArgumentsJson="{}"}};
    xllm_message messages[] = {{.eRole=XLLM_ROLE_USER,.sContent="hello"},
        {.eRole=XLLM_ROLE_ASSISTANT,.pToolCalls=calls,.iToolCallCount=3},
        {.eRole=XLLM_ROLE_TOOL,.sToolCallId="two",.pParts=&image_only,.iPartCount=1},
        {.eRole=XLLM_ROLE_TOOL,.sToolCallId=id,.pParts=mixed,.iPartCount=5},
        {.eRole=XLLM_ROLE_TOOL,.sToolCallId="three",.sContent="final tool text"},
        {.eRole=XLLM_ROLE_USER,.sContent="followup"}};
    xllm_message original[6]; xllm_part original_parts[5];
    xllm_client client={0}; xllm_error error={0};
    xllm_dialect_ops dialect={.BuildRequest=xllm__completions_build_request,
        .BuildRequestCached=xllm__completions_build_request_cached};
    xllm_request request={.pMessages=messages,.iMessageCount=6};
    xllm_allocator allocator={qa_alloc,qa_realloc,qa_free};
    char* expected; size_t cut, iteration, allocation_count, failures=0;
    memcpy(original,messages,sizeof(messages)); memcpy(original_parts,mixed,sizeof(mixed));
    client.sModel="fixture"; client.pDialect=&dialect;
    xllm__set_allocator(&allocator);
    expected=xllm__completions_build_request(&client,&request,&error); assert(expected);
    puts(expected);
    /* Keep a libc-owned oracle body outside the instrumented lifetime. */
    { char* copy=malloc(strlen(expected)+1); assert(copy); strcpy(copy,expected); xllm__free(expected); expected=copy; }
    assert(!live_blocks);
    for (cut=0; cut<=6; ++cut) {
        request.pStablePrefixOwner=&request; request.uStablePrefixStamp=1;
        request.iMessageCount=cut ? cut : 1; request.iStableMessages=cut;
        { char* seed=xllm__client_serialize_body(&client,&request,&error); assert(seed); xllm__free(seed); }
        request.iMessageCount=6; request.iStableMessages=cut;
        for (iteration=0; iteration<3; ++iteration) same_body(&client,&request,expected);
        assert(client.pPrefixCacheOwner == NULL);
        /* A later text-only request with the same owner/stamp must not reuse
         * expanded old bytes, even when its stable count is larger. */
        { xllm_message plain[6];
          for (iteration=0; iteration<6; ++iteration)
              plain[iteration]=(xllm_message){.eRole=XLLM_ROLE_USER,.sContent="new text only"};
          request.pMessages=plain; request.iMessageCount=6; request.iStableMessages=6;
          char* body=xllm__client_serialize_body(&client,&request,&error);
          assert(body && strstr(body,"new text only") && !strstr(body,"tool_call_id"));
          same_body(&client,&request,body);
          xllm__free(body); }
        request.pMessages=messages; reset_cache(&client);
    }
    /* Fail every xllm allocation/reallocation in the full cached request. */
    request.iMessageCount=request.iStableMessages=6; alloc_step=0; fail_step=0;
    same_body(&client,&request,expected); allocation_count=alloc_step; reset_cache(&client);
    for (fail_step=1; fail_step<=allocation_count; ++fail_step) {
        size_t failure=fail_step;
        alloc_step=0;
        char* body=xllm__client_serialize_body(&client,&request,&error);
        assert(body == NULL); ++failures;
        assert(client.iPrefixCacheLen == 0 && client.iPrefixCacheMessages == 0);
        fail_step=0; same_body(&client,&request,expected); reset_cache(&client);
        fail_step=failure;
    }
    fail_step=0;
    mixed[1].eKind=(xllm_part_kind)999;
    assert(xllm__client_serialize_body(&client,&request,&error) == NULL);
    mixed[1].eKind=XLLM_PART_IMAGE; same_body(&client,&request,expected); reset_cache(&client);
    /* Ordinary text-only caching still reuses its stable prefix. */
    request.iMessageCount=request.iStableMessages=1;
    { char* first=xllm__client_serialize_body(&client,&request,&error); assert(first);
      assert(client.pPrefixCacheOwner == &request);
      same_body(&client,&request,first); xllm__free(first); }
    reset_cache(&client);
    assert(memcmp(original,messages,sizeof(messages)) == 0);
    assert(memcmp(original_parts,mixed,sizeof(mixed)) == 0);
    assert(memcmp(png,"\211PNG\r\n\032\n\0\0\0\0",sizeof(png)) == 0);
    free(expected); xllm__set_allocator(NULL);
    printf("{\"cache_cuts\":7,\"oom_failures\":%zu,\"live_blocks\":%zu,\"source_unchanged\":true}\n", failures,live_blocks);
    return 0;
}
