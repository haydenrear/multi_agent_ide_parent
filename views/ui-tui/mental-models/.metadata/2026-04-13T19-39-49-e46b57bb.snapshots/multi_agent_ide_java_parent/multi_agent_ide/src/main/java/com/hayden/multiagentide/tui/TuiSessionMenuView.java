package com.hayden.multiagentide.tui;

import org.springframework.shell.component.view.control.MenuView;
import org.springframework.shell.component.view.event.KeyHandler;

class TuiSessionMenuView extends MenuView {

    @Override
    public KeyHandler getKeyHandler() {
        return KeyHandler.neverConsume();
    }

    @Override
    public KeyHandler getHotKeyHandler() {
        return KeyHandler.neverConsume();
    }
}
