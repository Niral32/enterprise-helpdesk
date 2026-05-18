package com.helpdesk.asset.exception;

/**
 * Exception thrown when an asset is not found
 */
public class AssetNotFoundException extends RuntimeException {
    public AssetNotFoundException(String message) {
        super(message);
    }

    public AssetNotFoundException(String message, Throwable cause) {
        super(message, cause);
    }
}
