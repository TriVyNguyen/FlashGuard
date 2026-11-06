package com.flashguard.exception;

import java.time.LocalDateTime;
import java.util.Map;

public record ErrorResponse(LocalDateTime timestamp, int status, ErrorCode code, String message, String path,
                            Map<String, String> fieldErrors) {
}
