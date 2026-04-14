package com.hayden.multiagentide.tui;

import com.hayden.acp_cdc_ai.acp.events.ArtifactKey;
import com.hayden.acp_cdc_ai.acp.events.EventBus;
import com.hayden.acp_cdc_ai.acp.events.EventListener;
import com.hayden.acp_cdc_ai.acp.events.Events;
import com.hayden.acp_cdc_ai.permission.IPermissionGate;
import com.hayden.multiagentide.cli.CliEventFormatter;
import com.hayden.multiagentide.repository.EventStreamRepository;
import com.hayden.multiagentide.ui.state.UiFocus;
import com.hayden.multiagentide.ui.state.UiSessionState;
import com.hayden.multiagentide.ui.state.UiState;
import com.hayden.multiagentide.ui.state.UiViewport;
import com.hayden.multiagentide.ui.shared.SharedUiInteractionService;
import com.hayden.multiagentide.ui.shared.UiActionCommand;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jline.terminal.Terminal;
import org.jline.terminal.Size;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.shell.component.view.TerminalUI;
import org.springframework.shell.component.view.TerminalUIBuilder;
import org.springframework.shell.component.view.control.View;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;

@Slf4j
@Component
@Profile("cli")
@RequiredArgsConstructor
public class TuiSession implements EventListener {

    @FunctionalInterface
    public interface GoalStarter {
        String startGoal(String repo, String goal);
    }

    private final EventStreamRepository eventStreamRepository;
    private final IPermissionGate permissionGateAdapter;
    private final CliEventFormatter eventFormatter;
    private final Terminal terminal;
    private final SharedUiInteractionService sharedUiInteractionService;

    private final Object stateLock = new Object();
    private final AtomicBoolean running = new AtomicBoolean(false);
    private static final int DEFAULT_COLUMNS = 80;
    private static final int DEFAULT_ROWS = 24;

    private EventBus eventBus;

    private GoalStarter goalStarter;
    private Path defaultRepoPath;
    private String shellSessionId;
    private String activeSessionId;
    private String activeNodeId;

    private TerminalUI terminalUi;
    private TuiTerminalView rootView;
    private UiState state;
    private final Map<String, Boolean> startedSessions = new LinkedHashMap<>();
    private volatile int eventListHeight = 10;

    @Autowired
    @Lazy
    public void setEventBus(EventBus eventBus) {
        this.eventBus = eventBus;
    }

    public void configureSession(String initialSessionId, Path defaultRepoPath, GoalStarter goalStarter) {
        this.goalStarter = goalStarter;
        this.defaultRepoPath = normalizeRepoPath(defaultRepoPath);
        this.activeSessionId = initialSessionId;
        this.activeNodeId = initialSessionId;
        this.startedSessions.put(initialSessionId, false);
    }

    public void run() {
        if (!running.compareAndSet(false, true)) {
            return;
        }

        try {
            this.shellSessionId = UUID.randomUUID().toString();
            ensureInitialState();
            syncTerminalSizeAtStartup();

            this.terminalUi = new TerminalUIBuilder(terminal).build();
            this.rootView = new TuiTerminalView(
                    this::snapshotState,
                    eventFormatter,
                    eventStreamRepository,
                    resolveDefaultRepoPath(),
                    new ViewController(),
                    height -> eventListHeight = Math.max(1, height),
                    this::setModalView,
                    this::configureDynamicView
            );

            terminalUi.configure(rootView);
            terminalUi.setRoot(rootView, true);
            terminalUi.setFocus(rootView);
            terminalUi.run();
        } finally {
            running.set(false);
            if (terminalUi != null) {
                terminalUi.setModal(null);
            }
            terminalUi = null;
            rootView = null;
        }
    }

    private void syncTerminalSizeAtStartup() {
        Size current = safeSize(terminal::getSize);
        int currentColumns = dimension(current, true);
        int currentRows = dimension(current, false);
        // Trust terminal-reported size when available. Forcing a guessed size
        // can lock in an incorrect width until a manual resize occurs.
        if (currentColumns > 0 && currentRows > 0) {
            return;
        }

        Size resolved = resolveStartupTerminalSize();
        if (resolved == null) {
            return;
        }
        try {
            terminal.setSize(resolved);
        } catch (Exception e) {
            log.debug("Failed to set startup terminal size: {}", e.getMessage());
        }
    }

    private Size resolveStartupTerminalSize() {
        Size terminalSize = safeSize(terminal::getSize);
        Size bufferSize = safeSize(terminal::getBufferSize);

        int terminalColumns = dimension(terminalSize, true);
        int terminalRows = dimension(terminalSize, false);
        int bufferColumns = dimension(bufferSize, true);
        int bufferRows = dimension(bufferSize, false);
        int envColumns = readEnvDimension("COLUMNS");
        int envRows = readEnvDimension("LINES");

        int columns = terminalColumns;
        int rows = terminalRows;

        if (columns <= 0 || rows <= 0) {
            columns = firstPositive(terminalColumns, bufferColumns, envColumns, DEFAULT_COLUMNS);
            rows = firstPositive(terminalRows, bufferRows, envRows, DEFAULT_ROWS);
        } else if (columns == DEFAULT_COLUMNS && rows == DEFAULT_ROWS) {
            int altColumns = firstPositive(bufferColumns, envColumns);
            int altRows = firstPositive(bufferRows, envRows);
            if (altColumns > 0 && altRows > 0 && (altColumns != columns || altRows != rows)) {
                columns = altColumns;
                rows = altRows;
            }
        }

        return new Size(firstPositive(columns, DEFAULT_COLUMNS), firstPositive(rows, DEFAULT_ROWS));
    }

    private Size safeSize(Supplier<Size> supplier) {
        try {
            return supplier.get();
        } catch (Exception ignored) {
            return null;
        }
    }

    private int dimension(Size size, boolean columns) {
        if (size == null) {
            return 0;
        }
        return columns ? size.getColumns() : size.getRows();
    }

    private int readEnvDimension(String key) {
        String value = System.getenv(key);
        if (value == null || value.isBlank()) {
            return 0;
        }
        try {
            int parsed = Integer.parseInt(value.trim());
            return parsed > 0 ? parsed : 0;
        } catch (NumberFormatException ignored) {
            return 0;
        }
    }

    private int firstPositive(int... values) {
        for (int value : values) {
            if (value > 0) {
                return value;
            }
        }
        return 0;
    }

    @Override
    public String listenerId() {
        return "cli-tui-session";
    }

    @Override
    public boolean isInterestedIn(Events.GraphEvent eventType) {
        return running.get();
    }

    @Override
    public void onEvent(Events.GraphEvent event) {
        if (!running.get()) {
            return;
        }

        Events.AddMessageEvent queuedMessage;
        synchronized (stateLock) {
            UiViewport viewport = new UiViewport(Math.max(1, eventListHeight));
            state = sharedUiInteractionService.reduce(
                    state,
                    event,
                    viewport,
                    sharedUiInteractionService.resolveNodeId(event, activeSessionId)
            );
            queuedMessage = resolveInteractionMessage(event);
        }

        if (terminalUi != null) {
            requestRedraw();
        }
        if (queuedMessage != null) {
            eventBus.publish(queuedMessage);
        }
    }

    private void setModalView(View view) {
        if (terminalUi == null) {
            return;
        }
        if (view != null) {
            terminalUi.configure(view);
        }
        terminalUi.setModal(view);
        if (view != null) {
            terminalUi.setFocus(view);
        } else if (rootView != null) {
            terminalUi.setFocus(rootView);
        }
        requestRedraw();
    }

    private void configureDynamicView(View view) {
        if (terminalUi == null || view == null) {
            return;
        }
        terminalUi.configure(view);
    }

    private void requestRedraw() {
        if (terminalUi == null) {
            return;
        }
        terminalUi.redraw();
    }

    private void ensureInitialState() {
        synchronized (stateLock) {
            if (activeSessionId == null || activeSessionId.isBlank()) {
                activeSessionId = "session-" + UUID.randomUUID();
                activeNodeId = activeSessionId;
                startedSessions.put(activeSessionId, false);
            }
            Map<String, UiSessionState> sessions = new LinkedHashMap<>();
            UiSessionState initial = UiSessionState.initial(resolveDefaultRepoPath());
            sessions.put(activeSessionId, initial);
            state = UiState.initial(shellSessionId, activeSessionId, List.of(activeSessionId), sessions, initial.repo());
        }
    }

    private UiState snapshotState() {
        synchronized (stateLock) {
            return state;
        }
    }

    private UiSessionState activeSessionState() {
        if (state == null || state.activeSessionId() == null) {
            return UiSessionState.initial(resolveDefaultRepoPath());
        }
        return state.sessions().getOrDefault(state.activeSessionId(), UiSessionState.initial(resolveDefaultRepoPath()));
    }

    private void publishInteraction(Events.UiInteractionEvent event) {
        String nodeId = (activeNodeId == null || activeNodeId.isBlank())
                ? (activeSessionId == null ? shellSessionId : activeSessionId)
                : activeNodeId;
        String interactionSessionId = state != null && state.activeSessionId() != null
                ? state.activeSessionId()
                : activeSessionId;

        Events.TuiInteractionGraphEvent graphEvent = new Events.TuiInteractionGraphEvent(
                UUID.randomUUID().toString(),
                Instant.now(),
                nodeId,
                interactionSessionId,
                event
        );
        eventBus.publish(graphEvent);
    }

    private void publishInteraction(UiActionCommand command) {
        publishInteraction(sharedUiInteractionService.toInteractionEvent(command));
    }

    private Events.AddMessageEvent resolveInteractionMessage(Events.GraphEvent event) {
        if (!(event instanceof Events.TuiInteractionGraphEvent interaction)) {
            return null;
        }
        if (!(interaction.tuiEvent() instanceof Events.ChatInputSubmitted submitted)) {
            return null;
        }

        String text = submitted.text();
        if (text == null || text.isBlank()) {
            return null;
        }

        String currentSession = state.activeSessionId();
        boolean goalStarted = startedSessions.getOrDefault(currentSession, false);
        if (!goalStarted) {
            startGoalForCurrentSession(text.trim(), currentSession);
            return null;
        }

        if (handlePendingPermissions(currentSession, text.trim()) || handlePendingInterrupts(currentSession, text.trim())) {
            return null;
        }

        return new Events.AddMessageEvent(
                UUID.randomUUID().toString(),
                Instant.now(),
                activeNodeId,
                text
        );
    }

    private void startGoalForCurrentSession(String goal, String sessionIdToReplace) {
        if (goalStarter == null || sessionIdToReplace == null) {
            return;
        }
        String repo = resolveRepoForSession(sessionIdToReplace);
        String startedNodeId = goalStarter.startGoal(repo, goal);
        if (startedNodeId == null || startedNodeId.isBlank()) {
            return;
        }

        Map<String, UiSessionState> sessions = new LinkedHashMap<>(state.sessions());
        UiSessionState existing = sessions.getOrDefault(sessionIdToReplace, UiSessionState.initial(resolveDefaultRepoPath()));
        sessions.remove(sessionIdToReplace);
        sessions.put(startedNodeId, existing);

        List<String> order = new ArrayList<>(state.sessionOrder());
        int idx = order.indexOf(sessionIdToReplace);
        if (idx >= 0) {
            order.set(idx, startedNodeId);
        } else if (!order.contains(startedNodeId)) {
            order.add(startedNodeId);
        }

        startedSessions.remove(sessionIdToReplace);
        startedSessions.put(startedNodeId, true);

        activeSessionId = startedNodeId;
        activeNodeId = startedNodeId;
        state = state.toBuilder()
                .activeSessionId(startedNodeId)
                .sessionOrder(List.copyOf(order))
                .sessions(sessions)
                .focus(UiFocus.CHAT_INPUT)
                .build();
    }

    private Path normalizeRepoPath(Path repoPath) {
        if (repoPath == null) {
            return null;
        }
        return repoPath.toAbsolutePath().normalize();
    }

    private Path resolveDefaultRepoPath() {
        if (defaultRepoPath != null) {
            return defaultRepoPath;
        }
        return Path.of(System.getProperty("user.dir")).toAbsolutePath().normalize();
    }

    private String resolveRepoForSession(String sessionId) {
        if (state != null && sessionId != null) {
            UiSessionState sessionState = state.sessions().get(sessionId);
            if (sessionState != null && sessionState.repo() != null) {
                return sessionState.repo().toString();
            }
        }
        return resolveDefaultRepoPath().toString();
    }

    private boolean handlePendingPermissions(String sessionId, String input) {
        IPermissionGate.PendingPermissionRequest request = findPendingPermissionForSession(sessionId);
        if (request == null) {
            return false;
        }
        if ("cancel".equalsIgnoreCase(input)) {
            permissionGateAdapter.resolveCancelled(request.getRequestId());
            return true;
        }
        List<com.agentclientprotocol.model.PermissionOption> options = request.getPermissions();
        if (!options.isEmpty()) {
            try {
                int index = Integer.parseInt(input);
                if (index >= 1 && index <= options.size()) {
                    permissionGateAdapter.resolveSelected(request.getRequestId(), options.get(index - 1));
                    return true;
                }
            } catch (NumberFormatException ignored) {
            }
        }
        permissionGateAdapter.resolveSelected(request.getRequestId(), input);
        return true;
    }

    private boolean handlePendingInterrupts(String sessionId, String input) {
        IPermissionGate.PendingInterruptRequest request = findPendingInterruptForSession(sessionId);
        if (request == null) {
            return false;
        }
        IPermissionGate.ResolutionType resolutionType;
        String resolutionNotes;
        if (input.isBlank()) {
            resolutionType = IPermissionGate.ResolutionType.RESOLVED;
            resolutionNotes = "";
        } else if ("approve".equalsIgnoreCase(input) || "approved".equalsIgnoreCase(input)) {
            resolutionType = IPermissionGate.ResolutionType.APPROVED;
            resolutionNotes = "approved";
        } else if ("reject".equalsIgnoreCase(input) || "rejected".equalsIgnoreCase(input)) {
            resolutionType = IPermissionGate.ResolutionType.REJECTED;
            resolutionNotes = "rejected";
        } else {
            resolutionType = IPermissionGate.ResolutionType.FEEDBACK;
            resolutionNotes = input;
        }
        permissionGateAdapter.resolveInterrupt(request.getInterruptId(), resolutionType, resolutionNotes, null);
        return true;
    }

    private IPermissionGate.PendingPermissionRequest findPendingPermissionForSession(String sessionId) {
        List<IPermissionGate.PendingPermissionRequest> pending = permissionGateAdapter.pendingPermissionRequests();
        if (pending == null || pending.isEmpty()) {
            return null;
        }
        String sessionRoot = resolveRootNodeId(sessionId);
        for (IPermissionGate.PendingPermissionRequest request : pending) {
            if (matchesSessionRoot(sessionRoot, request.getOriginNodeId())
                    || matchesSessionRoot(sessionRoot, request.getNodeId())) {
                return request;
            }
        }
        return null;
    }

    private IPermissionGate.PendingInterruptRequest findPendingInterruptForSession(String sessionId) {
        List<IPermissionGate.PendingInterruptRequest> pending = permissionGateAdapter.pendingInterruptRequests();
        if (pending == null || pending.isEmpty()) {
            return null;
        }
        String sessionRoot = resolveRootNodeId(sessionId);
        for (IPermissionGate.PendingInterruptRequest request : pending) {
            if (matchesSessionRoot(sessionRoot, request.getOriginNodeId())) {
                return request;
            }
        }
        return null;
    }

    private boolean matchesSessionRoot(String sessionRoot, String nodeId) {
        if (sessionRoot == null || sessionRoot.isBlank() || nodeId == null || nodeId.isBlank()) {
            return false;
        }
        return sessionRoot.equals(resolveRootNodeId(nodeId));
    }

    private String resolveRootNodeId(String nodeId) {
        if (nodeId == null || nodeId.isBlank()) {
            return null;
        }
        try {
            ArtifactKey artifactKey = new ArtifactKey(nodeId);
            if (artifactKey.isRoot()) {
                return artifactKey.value();
            }

            return artifactKey.root().value();
        } catch (Exception ignored) {
            return nodeId;
        }
    }

    private String resolveSessionId(Events.GraphEvent event) {
        if (event instanceof Events.TuiInteractionGraphEvent interaction) {
            return interaction.sessionId();
        }
        String nodeId = event == null ? null : event.nodeId();
        if (nodeId == null || nodeId.isBlank()) {
            return state == null ? activeSessionId : state.activeSessionId();
        }
        return resolveRootNodeId(nodeId);
    }

    private void moveSessionSelection(int delta) {
        List<String> order = state.sessionOrder();
        if (order.isEmpty()) {
            return;
        }
        int index = order.indexOf(state.activeSessionId());
        if (index < 0) {
            index = 0;
        }
        int next = Math.max(0, Math.min(order.size() - 1, index + delta));
        publishInteraction(new Events.SessionSelected(order.get(next)));
    }

    private void createNewSession() {
        String newSessionId = "session-" + UUID.randomUUID();
        startedSessions.put(newSessionId, false);
        publishInteraction(new Events.SessionCreated(newSessionId));
        publishInteraction(new Events.SessionSelected(newSessionId));
        publishInteraction(new Events.FocusChatInput(UiFocus.SESSION_LIST.name()));
    }

    private final class ViewController implements TuiTerminalView.Controller {

        @Override
        public void moveSelection(int delta) {
            synchronized (stateLock) {
                if (activeSessionState().detailOpen() && rootView != null) {
                    rootView.scrollDetail(delta);
                    requestRedraw();
                    return;
                }
                if (state.focus() == UiFocus.SESSION_LIST) {
                    moveSessionSelection(delta);
                    return;
                }
                if (state.focus() == UiFocus.CHAT_SEARCH) {
                    publishInteraction(new Events.ChatSearchResultNavigate(delta, activeSessionState().chatSearch().selectedResultIndex()));
                    return;
                }
                if (state.focus() == UiFocus.EVENT_STREAM) {
                    int target = activeSessionState().selectedIndex() + delta;
                    publishInteraction(new Events.EventStreamMoveSelection(delta, target));
                    return;
                }
                publishInteraction(new Events.FocusEventStream(state.focus().name()));
            }
        }

        @Override
        public void scrollList(int delta) {
            synchronized (stateLock) {
                if (state.focus() != UiFocus.EVENT_STREAM) {
                    return;
                }
                int target = activeSessionState().scrollOffset() + delta;
                publishInteraction(new Events.EventStreamScroll(delta, target));
            }
        }

        @Override
        public void handleEnter() {
            synchronized (stateLock) {
                if (state.focus() == UiFocus.CHAT_INPUT) {
                    publishInteraction(new Events.ChatInputSubmitted(activeSessionState().chatInput()));
                    publishInteraction(new Events.ChatInputChanged("", 0));
                    return;
                }
                if (state.focus() == UiFocus.SESSION_LIST) {
                    publishInteraction(new Events.SessionSelected(state.activeSessionId()));
                    return;
                }
                if (state.focus() == UiFocus.CHAT_SEARCH) {
                    publishInteraction(new Events.ChatSearchResultNavigate(1, activeSessionState().chatSearch().selectedResultIndex()));
                    return;
                }
                if (state.focus() == UiFocus.EVENT_STREAM) {
                    UiSessionState sessionState = activeSessionState();
                    if (!sessionState.events().isEmpty()) {
                        Events.GraphEvent selected = sessionState.events().get(sessionState.selectedIndex());
                        publishInteraction(new Events.EventStreamOpenDetail(selected.eventId()));
                    }
                }
            }
        }

        @Override
        public void handleBackspace() {
            synchronized (stateLock) {
                UiSessionState sessionState = activeSessionState();
                if (sessionState.detailOpen()) {
                    publishInteraction(new Events.EventStreamCloseDetail(sessionState.detailEventId()));
                    return;
                }
                if (state.focus() == UiFocus.CHAT_INPUT) {
                    String text = sessionState.chatInput();
                    if (!text.isEmpty()) {
                        String next = text.substring(0, text.length() - 1);
                        publishInteraction(new Events.ChatInputChanged(next, next.length()));
                    }
                    return;
                }
                if (state.focus() == UiFocus.CHAT_SEARCH && sessionState.chatSearch().active()) {
                    String query = sessionState.chatSearch().query();
                    if (!query.isEmpty()) {
                        String next = query.substring(0, query.length() - 1);
                        publishInteraction(new Events.ChatSearchQueryChanged(next, next.length()));
                    }
                }
            }
        }

        @Override
        public void handleEscape() {
            synchronized (stateLock) {
                UiSessionState sessionState = activeSessionState();
                if (sessionState.detailOpen()) {
                    publishInteraction(new Events.EventStreamCloseDetail(sessionState.detailEventId()));
                    return;
                }
                if (state.focus() == UiFocus.CHAT_SEARCH && sessionState.chatSearch().active()) {
                    publishInteraction(new Events.ChatSearchClosed(sessionState.chatSearch().query()));
                }
            }
        }

        @Override
        public void toggleFocus() {
            synchronized (stateLock) {
                if (state.focus() == UiFocus.EVENT_STREAM || state.focus() == UiFocus.SESSION_LIST) {
                    publishInteraction(new Events.FocusChatInput(state.focus().name()));
                } else {
                    publishInteraction(new Events.FocusEventStream(state.focus().name()));
                }
            }
        }

        @Override
        public void focusSessionList() {
            synchronized (stateLock) {
                publishInteraction(new Events.FocusSessionList(state.focus().name()));
            }
        }

        @Override
        public void createNewSession() {
            synchronized (stateLock) {
                TuiSession.this.createNewSession();
            }
        }

        @Override
        public void focusEventStream() {
            synchronized (stateLock) {
                publishInteraction(new Events.FocusEventStream(state.focus().name()));
            }
        }

        @Override
        public void openSearch() {
            synchronized (stateLock) {
                publishInteraction(new Events.ChatSearchOpened(activeSessionState().chatSearch().query()));
            }
        }

        @Override
        public void handlePrintable(char ch) {
            synchronized (stateLock) {
                if (state.focus() == UiFocus.CHAT_INPUT) {
                    String next = activeSessionState().chatInput() + ch;
                    publishInteraction(new Events.ChatInputChanged(next, next.length()));
                    return;
                }
                if (state.focus() == UiFocus.CHAT_SEARCH) {
                    String next = activeSessionState().chatSearch().query() + ch;
                    publishInteraction(new Events.ChatSearchQueryChanged(next, next.length()));
                }
            }
        }

        @Override
        public void selectSession(String sessionId) {
            synchronized (stateLock) {
                if (sessionId != null && !sessionId.isBlank()) {
                    publishInteraction(new Events.SessionSelected(sessionId));
                }
            }
        }
    }

    public UiState snapshotForTests() {
        synchronized (stateLock) {
            return state;
        }
    }

    public int eventListHeightForTests() {
        return eventListHeight;
    }
}
