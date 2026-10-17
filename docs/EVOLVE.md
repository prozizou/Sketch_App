# Evolve: the developers' community

**Evolve** is a community page inside Sketchware NWS: publish blocks, more blocks, XML layouts, Java and Kotlin
files and custom components; discover, like, comment, follow, save and import what others published.
It opens from the main screen's menu (**Evolve community**). The Logic editor's Evolve tab still offers its quick
sheet (pick / share blocks) over the same data.

## The page

| Part | What it does |
|---|---|
| Header | instant search (title, description, author, tags, file name, code), notifications bell with unread badge |
| Chips | All, Blocks, XML, Java, More blocks, Kotlin, Components |
| Tabs | Popular (likes + comments + downloads, then recency), Recent, Following (people you follow) |
| Cards | avatar, author, date, kind, tags, description, syntax-highlighted code (or the first screenshot), like, comment, save, share counters |
| Bottom navigation | Explore, Saved (your favorites), Profile (your posts, followers / following, bio) |
| Floating button | **Publish**: title, description, kind, tags (5 max), version, minimum Android, dependencies, up to 4 screenshots, content picked from the project |
| Post page | full code with copy, screenshots, comments with replies and @mentions, follow author, report, edit / delete (author), import |
| Notifications | likes, comments, replies, mentions, followers; swipe / mark read; live |

Everything is live (Realtime Database listeners). Loading is paged (20 per page, "load more" at the end of the
list), the last 40 posts are cached on disk so the page opens offline (a banner says so), and `DiffUtil` updates
only the cards that changed.

## Importing

A post is never executed: it is text. **Add to project** shows a preview and a list of checks before anything is
written, and everything it writes can be undone:

| Kind | Added to | Checks |
|---|---|---|
| `blocks` | My Block Collection (global) | name clash -> free name; raw Java blocks ask for confirmation |
| `moreblock` | More Block collection (global) | same |
| `view` | a new layout file of the chosen project | XML well-formed, name clash -> renamed (or replace), lists the `@drawable/..` the XML needs |
| `java` / `kotlin` | the chosen project's Java files | must parse (Java), package rewritten to the project's, third-party imports not in the project's libraries listed, minSdk vs the project's, file clash -> explicit **Replace** |
| `component` | custom components (global) | JSON checked, name clash -> explicit **Replace** |

**Copy** puts the code on the clipboard; **Download** saves the file in the app's folder.

## Firebase setup (once)

1. Realtime Database and Authentication (Google) are already needed by the app.
2. Enable **Storage** (screenshots) in the Firebase console.
3. Deploy the rules: `docs/evolve-database.rules.json` (Realtime Database > Rules) and `docs/evolve-storage.rules`
   (Storage > Rules), or `firebase deploy --only database,storage` with those files.
4. Moderators: add their uid as `true` under `Evolve_moderators/<uid>` (console only; the app can't write it).
   Moderators can delete any post or comment. Reports land in `Blocks_share_reports/<postId>/<uid>`
   (read them in the console).
5. Indexes are declared in the rules file (`.indexOn`).

## Security

- Every read and write needs a signed-in user; authors are checked against `auth.uid`.
- The server validates every field (lengths, kinds, tags, URLs only from Firebase Storage, counters).
  A like or an unlike must carry the matching like record in the same update; downloads and comments move one step
  at a time.
- Screenshots: JPEG under 500 KB, in the owner's own folder only. Text is shown as text (no HTML, no links
  followed), mentions and tags are restricted to safe characters, spam (repeated text, link floods) is refused by
  the app, and users can report posts.
- Shared code is never compiled or run by the app on its own.

## Tests

- App: `./gradlew :app:testDebugUnitTest --tests 'pro.sketchware.evolve.*'` (rules, feed, cache, import planner,
  highlighter) and `pro.sketchware.ui.EvolveCommunityScreenTest` (cards, post page, layouts in both themes).
- Rules: `cd scripts/evolve-rules-test && npm install && npm test` runs the database and storage rules against the
  Firebase emulators (Java 11+ needed).

## Data (below: the original description, still valid for the base fields)

### Node `Blocks_share`

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

## Database rules

The full rules are `docs/evolve-database.rules.json` (publish in Firebase console > Realtime Database > Rules) and
`docs/evolve-storage.rules` (Storage > Rules); they cover every node below, and `scripts/evolve-rules-test` checks
them on the emulators. Nodes: `Blocks_share` (posts), `Evolve_likes/<post>/<uid>` and `Evolve_liked/<uid>/<post>`,
`Evolve_saved/<uid>/<post>`, `Evolve_follows/<uid>/<other>`, `Evolve_followers/<other>/<uid>`,
`Evolve_comments/<post>/<id>`, `Evolve_notifications/<uid>/<id>`, `Evolve_profiles/<uid>`,
`Blocks_share_reports/<post>/<uid>` (write-only for users) and `Evolve_moderators/<uid>`. Post fields added to the
table above: `tags`, `version`, `minSdk`, `dependencies`, `screenshots`, `updatedAt`, `likes`, `commentCount`, `code`
and `fileName` (kinds `java`, `kotlin`, `component` keep their text in `code`).

## The Logic editor sheet (quick access)

- **Header**: title, refresh, Share. **Search** (instant) matches title, author, description, identifier and opcodes (and
  the XML of layouts). **Chips**: All (newest first), Popular (most picked first), Recent (last 7 days), My uploads, and,
  where the screen offers several kinds, All types / Blocks / More blocks / XML layouts.
- **Cards**: title, author and date, blocks (or elements) and downloads, description, the blocks drawn like the drawer
  draws them (or the first lines of the XML), the opcodes line. One column on a phone, two or three on a tablet.
- **Pick** is the main action. The **menu** has Preview (an inspection of everything the entry would bring in), Share
  (a text to send to somebody), Report (inappropriate, malicious, spam, other: written to `Blocks_share_reports`) and
  Delete (the owner, or a moderator; always confirmed).
- **Before adding** anything: an entry with raw Java code, or made with a newer version of the app, opens the inspection
  (the Java it would write, the variables it uses, the blocks) and adds nothing until you confirm. A name or content you
  already have asks before adding another copy. Nothing is ever run automatically.
- **Loading**: 30 entries at a time, the next page when you scroll to the end (or when a filter leaves few rows),
  skeleton cards on the first load, the last list kept for 5 minutes so reopening is instant, and an error text with
  **Retry**. Search and chips work on what is loaded.

## Offers to share

Evolve is also offered right after you save something: when you save a widget to your collection (as XML), when you
drop a block on the collection area, and, for a more block, through the **Evolve** option of its menu in the Events list
(next to Edit, Reset and Delete).

## Moderators

A moderator is a uid set by hand in the console: `Evolve_moderators/<uid>: true`. The app shows Delete on every entry to
them and the rules let the delete through.

## Limits

Posts are not versioned (the author can edit text, tags and screenshots, or delete and publish again), comment counters
are only checked to move one step at a time, and the reports are read in the Firebase console.
