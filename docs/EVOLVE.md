# Evolve: blocks, more blocks and layouts shared by the community

The third tab of the Logic editor's bottom bar opens **Evolve** (it replaced the "Components" tab, whose blocks are
all in the Blocks tab). Evolve lists what other users shared, lets you **Pick** an entry, and lets you **Share**
something of yours. Three kinds of entries exist, and each screen offers the kinds it can use:

| Kind (`kind`) | Shared from | Pick does |
|---|---|---|
| `blocks` (a stack) | Logic editor: a block of *My Block Collection*, or **any stack on the canvas** (control, list, view, component, more block calls...) | adds it to My Block Collection and the drawer |
| `moreblock` | Logic editor: one of the open screen's more blocks | imports it into the screen (the importer asks about name clashes and adds missing variables and resources) |
| `view` (XML) | **XML editor** (menu, "Evolve"): the layout as it is in the editor | asks: replace the layout, insert at the cursor (both can be undone), or copy the XML |

In the Logic editor, chips filter the list (All, Blocks, More blocks). The XML editor shows XML layouts only.

## Using it

- **Pick** (blocks): adds the stack to My Block Collection (under a free name: "Login", "Login (2)"...) and to the
  drawer. Nothing is added to your event until you drag it from the drawer, as for any collection block.
- **Share**: choose what to share (the list says where each item comes from), give it a name and an optional
  description, confirm.
  The sheet shows your name (Google display name, never your e-mail address), the date, the number of blocks and the
  number of downloads. You can delete what you shared.
- A stack that contains **raw Java code** blocks ("Add source directly", "Custom import") is flagged, and picking it
  asks for confirmation: those blocks write the author's Java straight into your app.
- Needs the Google sign-in and a build with Firebase configured; otherwise the sheet says so.

## Data: node `Blocks_share`

One child per shared block, keyed by a push id.

| Key | Type | Meaning |
|---|---|---|
| `name` | string, 1-60 | name shown, and the name in the picker's collection |
| `description` | string, 0-300 | what it does |
| `authorUid` | string | Firebase uid of the publisher; only they may delete the entry |
| `authorName` | string | Google display name of the publisher |
| `createdAt` | number | server time (ms since epoch), set with `ServerValue.TIMESTAMP` |
| `appVersion` | number | `versionCode` of the app that published it |
| `kind` | string | `blocks`, `moreblock` or `view`; missing means `blocks` (entries from before kinds) |
| `blockCount` | number, 1-1000 | blocks in the stack (200 at most), or elements of an XML layout |
| `opCodes` | list of strings | distinct opcodes used |
| `usesRawCode` | boolean | contains `addSourceDirectly` or `customImport` |
| `downloads` | number | times picked; +1 per pick |
| `blocksJson` | string, at most 200 000 chars | `blocks`, `moreblock`: the stack as a JSON array in the project-file block format |
| `spec` | string, at most 500 | `moreblock` only: the more block's spec, like `calc %d.a %d.b` |
| `xml` | string, at most 200 000 chars | `view` only: the layout's XML (well-formed, checked before sharing and before picking) |

The app never trusts `usesRawCode`: it recomputes it from the decoded blocks before picking.

## Database rules to publish

The app can't set these; they go in Firebase console > Realtime Database > Rules. Without rules that allow it,
loading and sharing fail with "permission denied by the database rules". Try them in the Rules Playground first.

```json
{
  "rules": {
    "Blocks_share": {
      ".indexOn": ["createdAt"],
      ".read": "auth != null",
      "$id": {
        ".write": "auth != null && (!data.exists() ? newData.child('authorUid').val() === auth.uid : (!newData.exists() && data.child('authorUid').val() === auth.uid))",
        ".validate": "newData.hasChildren(['name', 'authorUid', 'createdAt']) && (newData.hasChild('blocksJson') || newData.hasChild('xml'))",
        "name": { ".validate": "newData.isString() && newData.val().length > 0 && newData.val().length <= 60" },
        "description": { ".validate": "newData.isString() && newData.val().length <= 300" },
        "authorUid": { ".validate": "newData.val() === auth.uid" },
        "authorName": { ".validate": "newData.isString() && newData.val().length <= 100" },
        "createdAt": { ".validate": "newData.val() === now" },
        "appVersion": { ".validate": "newData.isNumber()" },
        "blockCount": { ".validate": "newData.isNumber() && newData.val() >= 1 && newData.val() <= 1000" },
        "kind": { ".validate": "newData.isString() && (newData.val() === 'blocks' || newData.val() === 'moreblock' || newData.val() === 'view')" },
        "spec": { ".validate": "newData.isString() && newData.val().length <= 500" },
        "xml": { ".validate": "newData.isString() && newData.val().length <= 200000" },
        "opCodes": { ".validate": "newData.hasChildren()" },
        "usesRawCode": { ".validate": "newData.isBoolean()" },
        "downloads": {
          ".write": "auth != null",
          ".validate": "newData.isNumber() && ((!data.exists() && newData.val() === 0) || newData.val() === data.val() + 1)"
        },
        "blocksJson": { ".validate": "newData.isString() && newData.val().length <= 200000" },
        "$other": { ".validate": false }
      }
    }
  }
}
```

What they do: any signed-in user reads; a user can create entries only as themselves and delete only their own; entries
can't be edited; the only change anyone else may make is `downloads` going up by one; unknown fields are refused.

## Not covered yet

There is no report button and no moderation tool: a harmful entry has to be deleted in the Firebase console (or by its
author). Entries are not versioned, and a shared stack can't be updated: delete it and share it again.
