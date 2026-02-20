goose acp can be replaced with args - ollama/openrouter:


Codex:

```
# Set default provider
model_provider = "openrouter"

# Define the OpenRouter provider
[model_providers.openrouter]
name = "OpenRouter"
base_url = "https://openrouter.ai/api/v1"
env_key = "OPENROUTER_API_KEY"
```

export OPENROUTER_API_KEY="sk-or-..."
