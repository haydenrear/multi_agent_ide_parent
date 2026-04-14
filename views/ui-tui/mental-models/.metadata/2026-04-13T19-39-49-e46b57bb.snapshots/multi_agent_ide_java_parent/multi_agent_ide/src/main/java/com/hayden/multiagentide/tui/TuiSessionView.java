package com.hayden.multiagentide.tui;

import com.hayden.multiagentide.ui.state.UiSessionState;
import com.hayden.multiagentide.ui.state.UiState;
import lombok.extern.slf4j.Slf4j;
import org.springframework.shell.component.view.control.GridView;
import org.springframework.shell.component.view.control.View;
import org.springframework.shell.component.view.screen.Screen;
import org.springframework.shell.geom.Rectangle;

import java.nio.file.Path;
import java.util.List;

@Slf4j
class TuiSessionView extends GridView {

    private static final int HEADER_HEIGHT = 4;
    private static final int MIN_STREAM_HEIGHT = 3;

    private final String sessionId;
    private final Path initialRepoPath;
    private final TuiHeaderView headerView;
    private final TuiMessageStreamView messageStreamView;
    private final TuiChatView chatView;

    private UiState state = null;
    private UiSessionState sessionState;

    TuiSessionView(String sessionId, Path initialRepoPath, TuiMessageStreamView messageStreamView) {
        this.sessionId = sessionId;
        this.initialRepoPath = initialRepoPath;
        this.headerView = new TuiHeaderView(initialRepoPath);
        this.messageStreamView = messageStreamView;
        this.chatView = new TuiChatView(initialRepoPath);
        this.sessionState = UiSessionState.initial(initialRepoPath);

        setShowBorders(false);
        setColumnSize(0);
        setRowSize(HEADER_HEIGHT, 0, 4);

        addItem(headerView, 0, 0, 1, 1, 0, 0);
        addItem(messageStreamView, 1, 0, 1, 1, 0, 0);
        addItem(chatView, 2, 0, 1, 1, 0, 0);
    }

    void update(UiState state, UiSessionState sessionState) {
        this.state = state;
        Path repo = sessionState != null && sessionState.repo() != null ? sessionState.repo() : initialRepoPath;
        this.sessionState = sessionState == null ? UiSessionState.initial(repo) : sessionState;
        this.headerView.update(this.state, sessionId, this.sessionState);
        this.messageStreamView.update(this.state, this.sessionState);
        this.chatView.update(this.state, this.sessionState);
    }

    int visibleEventRows() {
        return messageStreamView.visibleRows();
    }

    String selectedEventId() {
        return messageStreamView.selectedEventId();
    }

    UiSessionState sessionState() {
        return sessionState;
    }

    List<View> allViews() {
        List<View> views = new java.util.ArrayList<>();
        views.add(this);
        views.add(headerView);
        views.addAll(messageStreamView.allViews());
        views.add(chatView);
        return List.copyOf(views);
    }

    @Override
    protected void drawInternal(Screen screen) {

        // Compute and apply row sizes BEFORE super draws children,
        // otherwise children are laid out with stale row proportions.
        Rectangle inner = getInnerRect();
        int totalHeight = Math.max(8, inner.height());
        int totalWidth = Math.max(10, inner.width());

        int chatHeight = chatView.requiredHeight(totalWidth);
        int maxChat = Math.max(3, totalHeight - HEADER_HEIGHT - MIN_STREAM_HEIGHT);
        chatHeight = Math.max(3, Math.min(maxChat, chatHeight));

        int streamHeight = Math.max(MIN_STREAM_HEIGHT, totalHeight - HEADER_HEIGHT - chatHeight);
        if (HEADER_HEIGHT + streamHeight + chatHeight > totalHeight) {
            streamHeight = Math.max(1, totalHeight - HEADER_HEIGHT - chatHeight);
        }

        setRowSize(HEADER_HEIGHT, streamHeight, chatHeight);

        super.drawInternal(screen);
    }
}
