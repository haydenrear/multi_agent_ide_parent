package com.hayden.multiagentide.tui;

import com.hayden.acp_cdc_ai.acp.events.Events;
import com.hayden.multiagentide.cli.CliEventFormatter;
import com.hayden.multiagentide.ui.state.UiFocus;
import com.hayden.multiagentide.ui.state.UiSessionState;
import com.hayden.multiagentide.ui.state.UiState;
import lombok.extern.slf4j.Slf4j;
import org.springframework.shell.component.view.control.GridView;
import org.springframework.shell.component.view.control.ListView;
import org.springframework.shell.component.view.control.cell.AbstractListCell;
import org.springframework.shell.component.view.event.KeyHandler;
import org.springframework.shell.component.view.screen.Screen;
import org.springframework.shell.geom.Rectangle;

import java.lang.reflect.Field;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.springframework.shell.component.view.control.View;

@Slf4j
class TuiMessageStreamView extends GridView {

    private static final Field LIST_START_FIELD;
    private static final Field LIST_POS_FIELD;

    static {
        Field start = null;
        Field pos = null;
        try {
            start = ListView.class.getDeclaredField("start");
            pos = ListView.class.getDeclaredField("pos");
            start.setAccessible(true);
            pos.setAccessible(true);
        } catch (Exception ignored) {
        }
        LIST_START_FIELD = start;
        LIST_POS_FIELD = pos;
    }

    private final CliEventFormatter formatter;
    private final ListView<EventLine> eventList;
    private final Path initialRepoPath;

    private UiState state = null;
    private UiSessionState sessionState;
    private int visibleRows = 1;

    TuiMessageStreamView(CliEventFormatter formatter, Path initialRepoPath) {
        this.formatter = formatter;
        this.initialRepoPath = initialRepoPath;
        this.eventList = new ListView<>(ListView.ItemStyle.NOCHECK);
        this.sessionState = UiSessionState.initial(initialRepoPath);

        setShowBorder(true);
        setRowSize(0);
        setColumnSize(0);

        eventList.setShowBorder(false);
        eventList.setCellFactory((listView, item) -> new ClippedListCell<>(item, listView.getItemStyle()));

        addItem(eventList, 0, 0, 1, 1, 0, 0);
    }

    void update(UiState state, UiSessionState sessionState) {
        this.state = state;
        Path repo = sessionState != null && sessionState.repo() != null ? sessionState.repo() : initialRepoPath;
        this.sessionState = sessionState == null ? UiSessionState.initial(repo) : sessionState;
    }

    int visibleRows() {
        return Math.max(1, visibleRows);
    }

    List<View> allViews() {
        return List.of(this, eventList);
    }

    @Override
    public KeyHandler getKeyHandler() {
        return KeyHandler.neverConsume();
    }

    @Override
    public KeyHandler getHotKeyHandler() {
        return KeyHandler.neverConsume();
    }

    String selectedEventId() {
        int selected = clamp(sessionState.selectedIndex(), sessionState.events().size());
        if (selected < 0 || selected >= sessionState.events().size()) {
            return null;
        }
        return sessionState.events().get(selected).eventId();
    }

    @Override
    protected void drawInternal(Screen screen) {

        String title = "Events total=" + sessionState.events().size();
        if (state != null && state.focus() == UiFocus.EVENT_STREAM) {
            title += " (focus)";
        }
        setTitle(title);

        // Prepare items and viewport BEFORE super draws the ListView,
        // otherwise the ListView renders with stale/reset start=0, pos=0.
        Rectangle inner = getInnerRect();
        int width = TuiTextLayout.safeContentWidth(inner.width());
        visibleRows = Math.max(1, inner.height());

        List<EventLine> lines = buildEventLines(width);
        eventList.setItems(lines);
        syncListViewport(lines.size(), visibleRows);

        super.drawInternal(screen);
    }

    private List<EventLine> buildEventLines(int width) {
        List<EventLine> items = new ArrayList<>(sessionState.events().size());
        for (Events.GraphEvent event : sessionState.events()) {
            String preview = formatter.format(event);
            String display = TuiTextLayout.truncateWithEllipsis(preview, width);
            items.add(new EventLine(event.eventId(), display));
        }
        return items;
    }

    private void syncListViewport(int size, int visible) {
        if (size <= 0 || LIST_START_FIELD == null || LIST_POS_FIELD == null) {
            return;
        }

        int selected = clamp(sessionState.selectedIndex(), size);
        int start;
        if (sessionState.autoFollow()) {
            start = Math.max(0, size - Math.max(1, visible));
        } else {
            start = Math.max(0, Math.min(sessionState.scrollOffset(), Math.max(0, size - 1)));
        }

        if (selected < start) {
            start = selected;
        }
        if (selected >= start + Math.max(1, visible)) {
            start = Math.max(0, selected - Math.max(1, visible) + 1);
        }

        int pos = Math.max(0, selected - start);
        try {
            LIST_START_FIELD.setInt(eventList, start);
            LIST_POS_FIELD.setInt(eventList, pos);
        } catch (IllegalAccessException ignored) {
        }
    }

    private int clamp(int index, int size) {
        if (size <= 0) {
            return 0;
        }
        if (index < 0) {
            return 0;
        }
        if (index >= size) {
            return size - 1;
        }
        return index;
    }

    private record EventLine(String eventId, String display) {
        @Override
        public String toString() {
            return display;
        }
    }

    /**
     * ListCell that clips text to the cell's rect width.
     * Spring Shell's default AbstractListCell.drawContent() writes the full text string
     * without any width clipping, which causes text to overwrite the parent's border.
     */
    private static class ClippedListCell<T> extends AbstractListCell<T> {

        ClippedListCell(T item, ListView.ItemStyle itemStyle) {
            super(item, itemStyle);
        }

        @Override
        protected void drawContent(Screen screen) {
            Rectangle rect = getRect();
            int maxWidth = Math.max(0, rect.width());
            String text = getText();
            if (text.length() > maxWidth) {
                text = text.substring(0, maxWidth);
            }
            Screen.Writer writer = screen.writerBuilder()
                    .style(getStyle())
                    .color(getForegroundColor())
                    .build();
            writer.text(text, rect.x(), rect.y());
        }
    }
}
