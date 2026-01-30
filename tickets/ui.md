Add the emitting of agent events within the ag-ui framework https://docs.ag-ui.com/introduction#agent-framework-community. 

Add the frontend in react with copilot kit https://docs.copilotkit.ai/direct-to-llm/guides/quickstart, in a multi_agent_ide/fe directory, and add the nodejs gradle plugin for building it into src/main/resources/static folder - the multi_agent_ide will be packaged with the react app and spring code will be 
deployed as the backend.

This includes for the backend:
- Add an event serializer for the WebSocketEventAdapter to map to the ag-ui events - started with AgUiSerdes, and in WebSocketEventAdapter already.

This includes for the frontend:
- Websocket connector and reader - building out the computation graph in memory, and displaying it on the frontend
- For different types of nodes and content deltas, adding different types of view plugins
- Adding all the knobs such as interrupt, add message, etc.


Add the a2ui https://a2ui.org/transports/#how-it-works data flow for allowing agents to emit events to the UI.
