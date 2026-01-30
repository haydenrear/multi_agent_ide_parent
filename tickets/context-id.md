See this

```md
Yes, you can skip particular fields in the schema so the framework fills them instead of the model. This is done through property filtering in the PromptRunner API.

Property Filtering Methods
The framework provides several ways to exclude fields from the LLM's schema:

Using withoutProperties()
data class User(  
    val id: String,           // Framework will set this  
    val name: String,         // LLM will fill this  
    val email: String,        // LLM will fill this  
    val internalMetadata: Map<String, Any>  // Framework will set this  
)  
  
@Action  
fun createUser(request: String, ai: Ai): User {  
    val user = ai.withDefaultLlm()  
        .creating(User::class.java)  
        .withoutProperties("internalMetadata")  // Exclude from schema  
        .fromPrompt("Create a user: $request")  
      
    // Framework fills the excluded field  
    return user.copy(id = generateId(), internalMetadata = mapOf("source" to "api"))  
}
Using Property Filters
ai.withPropertyFilter { propertyName ->   
    propertyName != "id" && !propertyName.startsWith("_")  
}  
    .createObject("Create user", User::class.java)
```

So we need to replace context id with artifact ID, and with our @artifact.md - and then set these as above. We'll also need to add them to emission in our @AcpChatModel of various events, and all events emitted by the model, and so this artifact logic will need to exist in utility module with the events emitted, and all of our models will need to be emitted with as artifact trees.
