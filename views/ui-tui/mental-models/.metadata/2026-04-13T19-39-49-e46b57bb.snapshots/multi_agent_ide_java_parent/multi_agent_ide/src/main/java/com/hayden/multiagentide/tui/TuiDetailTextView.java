package com.hayden.multiagentide.tui;

import lombok.extern.slf4j.Slf4j;
import org.springframework.shell.component.view.control.BoxView;
import org.springframework.shell.component.view.screen.Screen;
import org.springframework.shell.geom.Rectangle;

import java.util.ArrayList;
import java.util.List;

@Slf4j
class TuiDetailTextView extends BoxView {

    private final String text;
    private int scrollOffset;

    TuiDetailTextView(String text) {
        this.text = text == null ? "" : text;
        this.scrollOffset = 0;
        setShowBorder(false);
    }

    void scroll(int delta) {
        scrollOffset = Math.max(0, scrollOffset + delta);
    }

    @Override
    protected void drawInternal(Screen screen) {
        super.drawInternal(screen);
        Rectangle inner = getInnerRect();
        int width = TuiTextLayout.safeContentWidth(inner.width());
        int height = Math.max(1, inner.height());

        List<String> lines = new ArrayList<>();
        for (String line : text.split("\\R")) {
            lines.addAll(TuiTextLayout.wrapWord(line, width));
        }
        if (lines.isEmpty()) {
            lines.add("");
        }

        int maxScroll = Math.max(0, lines.size() - height);
        if (scrollOffset > maxScroll) {
            scrollOffset = maxScroll;
        }

        Screen.Writer writer = screen.writerBuilder().layer(getLayer()).build();
        for (int row = 0; row < height; row++) {
            int idx = scrollOffset + row;
            String value = idx < lines.size() ? lines.get(idx) : "";
            String pad = TuiTextLayout.pad(value, width);
            writer.text(pad, inner.x(), inner.y() + row);
        }
    }
}
