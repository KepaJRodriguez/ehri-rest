# Identifiers

There are two kinds of identifier concepts in the EHRI DB: local, and global. Unfortunately we haven't been too
thorough about disambiguating the two things, so it's quite confusing in places. If we use the word 'identifier' it
typically means the local identifier, whereas the word ID means the global one. Here's what they mean in practice:

Local identifiers exist within a namespace defined by their parent scope. Item types at the top level of a hierarchy - for example, countries - have an identifier that consists of their ISO 3166 two-letter country code. Since they have
no higher scope, their local identifier is the same as their global one: for example, the Netherlands has local
identifier `nl` and also global identifier `nl`.

Repositories, however, belong to a scope (the country in which they  reside)  so *their* global identifier consists
of the local identifier of their scope *added to* their own local identifier. For example, the local identifier of
USHMM is `005578` and that of it's county (the U.S.) `us`, therefore USHMM's global identifier is `us-005578`.

## Why we do this

Identifiers are only useful when they uniquely identify something. However, identity within hierarchies is contextual
. For example, within an archival collection `c1` there can only be a single archival unit with the identifier `1`.
Deriving the *global identifier* of an item from it's own local identifier plus that of it's parent items therefore
provides a means to ensure uniqueness within a given hierarchical scope - if the resulting global ID is already
taken, the local identifier is not unique within it's scope.

For example, if we import an EAD file from repository `001500` in country `us` with the following structure:

```xml
<archdesc>
    <did>
        <unitid>100</unitid>
    </did>
    <c01>
        <did>
            <unitid>1</unitid>
        </did>
    </c01>
</archdesc>
```

... the resultant global ID of the first unitid would be `us-001500-100` and that of it's child item `us-001500-100-1`.

Relative identifiers are therefore preferred in EHRI since they provide the neatest global identifiers. However in
many cases EAD files are structured with absolute identifiers, e.g:

```xml
<archdesc>
    <did>
        <unitid>100</unitid>
    </did>
    <c01>
        <did>
            <unitid>100 1</unitid>
        </did>
    </c01>
</archdesc>
```

In this case, the resultant global ID of the first unitid would be `us-001500-100` and that of it's child item
`us-001500-100-100-1`. There is nothing we can realistically do in EHRI to prevent this redundancy in the global
identifiers if that is how the source institution has chosen to represent their material.

## Trade-offs

The main trade-off in this scheme is normalisation vs. ease of determining uniqueness. It is quite difficult (and
quite costly) to determine if a given identifier is unique within the scope of its parent item. (In the worst case it
involves iterating through every single node in the graph, which makes importing items exceedingly slow.) Creating
graph IDs from a concatenation of local identifiers with the parent scopes allows uniqueness checks via a single
index lookup, which is very cheap. The downside is that an item's graph ID is de-normalised with the hierarchical
structure to which it belongs. If it is moved to another parent scope, it's graph ID will no longer be valid. For this
reason we recommend that moving an item within a hierarchy be though of as a copy followed by a delete.






