
Embabel should change Predicate<String> filter to add Predicate<Field> or skipAnnotation options.

Also - embabel should be able to skip fields from the json schema - DEFINITELY - this right here is a huge feature.

To be able to add annotation SkipSchema helps a lot for prompting - if you can skip for instance parts of your data 
model you know the AI won't use, then the AI can be much smaller - because one field can be deeply nested info - and
you're passing around your data model - it's not guaranteed to be that shallow- and some data model can be implements
SkipSchema ?.

Additionally, SomeOf deserialization should, if fails whole object, iterate through each of them and try serialize. This
can be opted into. Some smaller models make this mistake.