            } else if ( pMessage->iPartCount > 0u ) {
                if ( !xllm__buf_append_char(pBuf, '[') ) { return false; }
                for ( j = 0u; j < pMessage->iPartCount; ++j ) {
                    if ( j && !xllm__buf_append_char(pBuf, ',') ) { return false; }
                    if ( !xllm__responses_append_content_part(pBuf,
                            &pMessage->pParts[j], false, pError) ) { return false; }
                }
                if ( !xllm__buf_append_cstr(pBuf, "]}") ) { return false; }
            } else if ( !xllm__json_string(pBuf, pMessage->sContent ? pMessage->sContent : "") ||
                        !xllm__buf_append_char(pBuf, '}') ) {
                return false;
            }
