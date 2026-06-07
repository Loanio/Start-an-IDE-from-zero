package com.zeroide.api;

/**
 * Entry point implemented by every dynamically loaded editor plugin.
 */
public interface Plugin {
    void onLoad(EditorContext context) throws Exception;

    void onUnload() throws Exception;
}
