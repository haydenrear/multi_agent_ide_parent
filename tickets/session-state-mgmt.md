- The session state and TUI state/reducer should be pulled out of just TUI - although it's updated in TUI we want to keep it general enough for any UI (as a snapshot of current state of UI that we can return)
- On goal complete we can then save this snapshot to a file or in the database and then we can pull up any session previously
- On goal complete we also then now can delete from all in memory repositories for keys in hierarchy below root session being deleted


