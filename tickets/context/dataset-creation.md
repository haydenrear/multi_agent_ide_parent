# Jaeger

We will do something like this:

```shell
curl -G "http://localhost:16686/api/traces" \
  --data-urlencode "service=multi-agent-ide" \
  --data-urlencode 'tags={"artifact.root":"[her]", "request.type": "action.start"}' \
  --data-urlencode "start=1772752779802000" \
  --data-urlencode "end=1772756379802000" \
  --data-urlencode "limit=200"
```

And then wrap our actions in there, and filter out the spans so it can see them. Then stream them to a dataframe,
and ask an AI to visualize them - then the AI will be able to have a great way of visualizing where in the process, 
and be able to connect that with the process.

- for this, we just need to add our annotation and tags.


---

