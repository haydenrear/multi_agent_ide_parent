package com.hayden.multiagentide.tui;

import com.hayden.multiagentide.ui.state.UiFocus;
import com.hayden.multiagentide.ui.state.UiSessionState;
import com.hayden.multiagentide.ui.state.UiState;
import lombok.extern.slf4j.Slf4j;
import org.springframework.shell.component.view.control.BoxView;
import org.springframework.shell.component.view.screen.Screen;
import org.springframework.shell.geom.Rectangle;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

@Slf4j
class TuiHeaderView extends BoxView {

    private final Path initialRepoPath;
    private String sessionId = "";
    private UiState state = null;
    private UiSessionState sessionState;

    TuiHeaderView(Path initialRepoPath) {
        this.initialRepoPath = initialRepoPath;
        this.sessionState = UiSessionState.initial(initialRepoPath);
        setShowBorder(true);
    }

    void update(UiState state, String sessionId, UiSessionState sessionState) {
        this.state = state;
        this.sessionId = sessionId == null ? "" : sessionId;
        Path repo = sessionState != null && sessionState.repo() != null ? sessionState.repo() : initialRepoPath;
        this.sessionState = sessionState == null ? UiSessionState.initial(repo) : sessionState;
    }

    @Override
    protected void drawInternal(Screen screen) {
        setTitle("Session " + sessionId);

        Rectangle inner = getInnerRect();
        int width = TuiTextLayout.safeContentWidth(inner.width());
        int y = inner.y();

        String focus = state == null || state.focus() == null ? UiFocus.CHAT_INPUT.name() : state.focus().name();
        String topLine = "events=" + sessionState.events().size() + " selected=" + sessionState.selectedIndex() + " focus=" + focus;
        String repoLine = "repo=" + abbreviate(sessionState.repo() == null ? "none" : sessionState.repo().toString(), width - 12);
        String keyLine = "Tab focus  Ctrl+S sessions  Ctrl+E events  Ctrl+F search  Ctrl+N new";

        List<String> lines = new ArrayList<>();
        lines.addAll(TuiTextLayout.wrapFixed(topLine, width));
        lines.addAll(TuiTextLayout.wrapFixed(repoLine, width));
        lines.addAll(TuiTextLayout.wrapFixed(keyLine, width));

        Screen.Writer writer = screen.writerBuilder().build();
        int row = 0;
        for (; row < Math.min(lines.size(), inner.height()); row++) {
            String line = TuiTextLayout.truncateWithEllipsis(lines.get(row), width);
            writer.text(TuiTextLayout.pad(line, width), inner.x(), y + row);
        }
        for (; row < inner.height(); row++) {
            writer.text(TuiTextLayout.pad("", width), inner.x(), y + row);
        }

        super.drawInternal(screen);
    }

    private String abbreviate(String text, int maxSize) {
        String sanitized = TuiTextLayout.sanitizeInline(text);
        if (sanitized.isBlank()) {
            return "none";
        }
        if (sanitized.length() <= Math.max(40, maxSize)) {
            return sanitized;
        }
        return sanitized.substring(0, Math.max(40, maxSize)) + "...";
    }

}
