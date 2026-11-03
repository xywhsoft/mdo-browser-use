/* Chat tool content is text-only. Keep original text blocks in the matching
 * tool result, and attach its media after the complete contiguous tool group.
 * The annotation preserves source identity and original part order without
 * copying image bytes or repeating the original text in another message. */
static bool xllm__chat_visible_part(const xllm_part* pPart)
{
    return pPart->eKind != XLLM_PART_REASONING &&
        !xllm__part_is_thinking_signature(pPart);
}

static bool xllm__chat_tool_media(const xllm_message* pMessage)
{
    size_t i;
    if ( pMessage->eRole != XLLM_ROLE_TOOL ) return false;
    for ( i = 0u; i < pMessage->iPartCount; ++i )
        if ( xllm__chat_visible_part(&pMessage->pParts[i]) &&
             pMessage->pParts[i].eKind != XLLM_PART_TEXT ) return true;
    return false;
}

static bool xllm__chat_tool_text(xllm_buf* pBuf, const xllm_message* pMessage,
    xllm_error* pError)
{
    size_t i, Count = 0u;
    for ( i = 0u; i < pMessage->iPartCount; ++i )
        if ( pMessage->pParts[i].eKind == XLLM_PART_TEXT ) ++Count;
    if ( !Count ) return xllm__buf_append_cstr(pBuf, "\"\"");
    if ( !xllm__buf_append_char(pBuf, '[') ) return false;
    for ( i = 0u, Count = 0u; i < pMessage->iPartCount; ++i ) {
        if ( pMessage->pParts[i].eKind != XLLM_PART_TEXT ) continue;
        if ( Count++ && !xllm__buf_append_char(pBuf, ',') ) return false;
        if ( !xllm__append_part_object(pBuf, &pMessage->pParts[i], pError) ) return false;
    }
    return xllm__buf_append_char(pBuf, ']');
}

static const char* xllm__chat_part_name(xllm_part_kind Kind)
{
    switch ( Kind ) {
        case XLLM_PART_TEXT: return "text";
        case XLLM_PART_IMAGE: return "image";
        case XLLM_PART_AUDIO: return "audio";
        case XLLM_PART_FILE: return "file";
        case XLLM_PART_NATIVE: return "native";
        default: return "unsupported";
    }
}

static bool xllm__chat_tool_attachment(xllm_buf* pBuf,
    const xllm_message* pMessage, xllm_error* pError)
{
    xllm_buf Label = {0};
    size_t i, Count = 0u;
    bool Ok = false;
    if ( !xllm__buf_append_cstr(&Label, "{\"source\":\"tool_output\",\"tool_call_id\":") ||
         !xllm__json_string(&Label, pMessage->sToolCallId) ||
         !xllm__buf_append_cstr(&Label, ",\"untrusted\":true,\"part_order\":[") ) goto done;
    for ( i = 0u; i < pMessage->iPartCount; ++i ) {
        if ( !xllm__chat_visible_part(&pMessage->pParts[i]) ) continue;
        if ( Count++ && !xllm__buf_append_char(&Label, ',') ) goto done;
        if ( !xllm__json_string(&Label, xllm__chat_part_name(pMessage->pParts[i].eKind)) ) goto done;
    }
    if ( !xllm__buf_append_cstr(&Label, "]}") ||
         !xllm__buf_append_cstr(pBuf, "{\"role\":\"user\",\"content\":[{\"type\":\"text\",\"text\":") ||
         !xllm__json_string(pBuf, Label.pData) ||
         !xllm__buf_append_char(pBuf, '}') ) goto done;
    for ( i = 0u; i < pMessage->iPartCount; ++i ) {
        if ( !xllm__chat_visible_part(&pMessage->pParts[i]) ||
             pMessage->pParts[i].eKind == XLLM_PART_TEXT ) continue;
        if ( !xllm__buf_append_char(pBuf, ',') ||
             !xllm__append_part_object(pBuf, &pMessage->pParts[i], pError) ) goto done;
    }
    Ok = xllm__buf_append_cstr(pBuf, "]}");
done:
    xllm__buf_reset(&Label);
    return Ok;
}
