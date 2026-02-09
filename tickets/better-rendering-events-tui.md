1. Certain tool calls, such as write and read should be able to be rendered as diffs, and the summary should show the particular file - so for these, we can map to a separate event type that can be rendered separately in the TUI
   - scripts should be easy to see the scrip first in summary
   - read/write should be easy to see file in summary, and rendered easy to see diff or read
   - scripts/commands should be able to see their results streamed into the page easily 
2. All tool call json should be rendered in a way that's easy to read -> json printer makes this easy
3. Messages, interrupt requests for human review, and permission requests should just be text

So effectively with these changes it should look as good as Zed -> and we'll prepare for rendering these events we map to on any UI

