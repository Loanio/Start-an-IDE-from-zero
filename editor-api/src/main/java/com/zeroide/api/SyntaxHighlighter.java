package com.zeroide.api;

import java.util.List;

public interface SyntaxHighlighter {
    List<HighlightSpan> highlight(String text);
}
