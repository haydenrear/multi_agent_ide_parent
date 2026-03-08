For context compaction, use injection in headers and hooks...  

```markdown
Yes. LiteLLM proxy can sit in front of Claude Code, run in Docker, and mutate the request before forwarding it to OpenRouter. That is the cleanest way to inject OpenRouter-specific request fields that Claude Code itself does not expose. LiteLLM supports an Anthropic-compatible /v1/messages endpoint for Claude Code and also supports pre-call hooks that can modify incoming requests before the upstream LLM call is made.  ￼

One important detail: OpenRouter’s current API reference documents the parameter as transforms (plural), an array of strings, not transform singular. Their message-transforms guide also describes enabling transforms by sending that request field.  ￼

A workable shape is:

Claude Code
  -> LiteLLM proxy (/v1/messages)
  -> pre-call hook adds "transforms": [...]
  -> OpenRouter

Claude Code already knows how to talk to an Anthropic-style endpoint, and LiteLLM exposes exactly that for /v1/messages. OpenRouter also documents that Claude Code can be pointed at a custom Anthropic base URL via ANTHROPIC_BASE_URL and ANTHROPIC_AUTH_TOKEN.  ￼

Docker setup

LiteLLM has official Docker deployment docs and published images. Their docs show docker.litellm.ai/berriai/litellm:main-latest for quick start, and their production docs also reference stable images.  ￼

docker-compose.yml

services:
  litellm:
    image: docker.litellm.ai/berriai/litellm:main-latest
    ports:
      - "4000:4000"
    env_file:
      - .env
    volumes:
      - ./config.yaml:/app/config.yaml
      - ./openrouter_transform_hook.py:/app/openrouter_transform_hook.py
    command: ["--config=/app/config.yaml", "--detailed_debug"]

.env

OPENROUTER_API_KEY=sk-or-...

config.yaml

model_list:
  - model_name: claude-code
    litellm_params:
      model: openrouter/anthropic/claude-sonnet-4.5
      api_key: os.environ/OPENROUTER_API_KEY
      api_base: https://openrouter.ai/api/v1

litellm_settings:
  set_verbose: true

callbacks: /app/openrouter_transform_hook.py

That basic pattern matches LiteLLM’s config-driven proxy setup and Docker deployment model.  ￼

Hook to inject OpenRouter transforms

LiteLLM’s async_pre_call_hook is the hook designed for this exact job: modify request data before the LLM call is made.  ￼

openrouter_transform_hook.py

from litellm.integrations.custom_logger import CustomLogger

class OpenRouterTransformHook(CustomLogger):
    async def async_pre_call_hook(
        self,
        user_api_key_dict,
        cache,
        data,
        call_type,
    ):
        """
        Runs before LiteLLM makes the upstream model call.
        `data` is the outbound request payload LiteLLM will send upstream.
        """

        # Only touch Claude-Code style message calls if you want to scope it.
        # You can remove this condition if you want it applied globally.
        if call_type in ("acompletion", "completion"):
            transforms = data.get("transforms") or []
            wanted = ["middle-out"]  # replace/add your transform keys here

            # preserve existing transforms and avoid duplicates
            for t in wanted:
                if t not in transforms:
                    transforms.append(t)

            data["transforms"] = transforms

        return data

The relevant point is that LiteLLM explicitly documents async_pre_call_hook for mutating incoming request data before the outbound LLM API call.  ￼

Point Claude Code at LiteLLM

Then set Claude Code to use your local LiteLLM proxy instead of OpenRouter directly:

export ANTHROPIC_BASE_URL="http://localhost:4000"
export ANTHROPIC_AUTH_TOKEN="anything"
export ANTHROPIC_API_KEY=""

OpenRouter’s Claude Code integration docs say Claude Code is configured through ANTHROPIC_BASE_URL and ANTHROPIC_AUTH_TOKEN, and they specifically note clearing ANTHROPIC_API_KEY to avoid conflicts.  ￼

What this buys you

With this arrangement:
	•	Claude Code still talks normal Anthropic /v1/messages
	•	LiteLLM receives the request and can mutate it
	•	LiteLLM forwards upstream to OpenRouter
	•	OpenRouter receives the extra transforms field and applies message transforms

That is the main advantage of using LiteLLM here: Claude Code stays unchanged, while the proxy becomes your programmable control plane. LiteLLM also gives you spend tracking, routing, logging, and other gateway features on top.  ￼

Two caveats

First, use transforms, not transform, unless OpenRouter gave you a private/internal integration contract that differs from the public API reference. The public docs currently show transforms?: string[].  ￼

Second, if you try to use Claude Code through LiteLLM with a provider/model path that is not actually compatible with the Anthropic-shaped parameters Claude Code sends, you can hit translation edge cases. LiteLLM has active docs for Claude Code usage, but there are also recent reports of incompatibilities when routing Claude Code traffic to some non-Anthropic backends. Keeping the upstream as OpenRouter’s Anthropic-compatible endpoint is the safer path here.  ￼

If you want, I can give you a ￼ fully working repo layout next: docker-compose.yml, config.yaml, hook file, and the exact shell commands to start it and verify the transform is being injected.
```

Spin up docker-compose.yml
- Proxy cxn for various providers - injecting the header, request, or potentially intercepting the request and performing 
some context compaction directly locally in the script itself.
- Different endpoint for different providers

Another way to do it would be to 

- Detect compaction required
- Call an endpiont on the service, with the session key, action, agent, etc, and recreate the prompt itself... 
    - I like this a bit better because we will know important stuff better. However, this openrouter thing may do the 
      same