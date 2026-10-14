# Evolve: blocks shared by the community

The third tab of the Logic editor's bottom bar opens **Evolve** (it replaced the "Components" tab, whose blocks are
all in the Blocks tab). Evolve lists the stacks of blocks other users shared, lets you **Pick** one into your own
*My Block Collection*, and lets you **Share** one of yours.

## Using it

- **Pick**: adds the stack to My Block Collection (under a free name: "Login", "Login (2)"...) and to the drawer.
  Nothing is added to your event until you drag it from the drawer, as for any collection block.
- **Share**: choose a block from My Block Collection, give it a name and an optional description, confirm.
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
| `blockCount` | number, 1-200 | blocks in the stack |
| `opCodes` | list of strings | distinct opcodes used |
| `usesRawCode` | boolean | contains `addSourceDirectly` or `customImport` |
| `downloads` | number | times picked; +1 per pick |
| `blocksJson` | string, at most 200 000 chars | the stack as a JSON array in the project-file block format |

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
        ".validate": "newData.hasChildren(['name', 'authorUid', 'blocksJson', 'createdAt'])",
        "name": { ".validate": "newData.isString() && newData.val().length > 0 && newData.val().length <= 60" },
        "description": { ".validate": "newData.isString() && newData.val().length <= 300" },
        "authorUid": { ".validate": "newData.val() === auth.uid" },
        "authorName": { ".validate": "newData.isString() && newData.val().length <= 100" },
        "createdAt": { ".validate": "newData.val() === now" },
        "appVersion": { ".validate": "newData.isNumber()" },
        "blockCount": { ".validate": "newData.isNumber() && newData.val() >= 1 && newData.val() <= 200" },
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
