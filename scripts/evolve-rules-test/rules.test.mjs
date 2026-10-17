// Tests of the Evolve security rules against the Firebase emulators.
//   npm install && npm test        (needs Java 11+ for the emulators)
import { test, before, after, beforeEach } from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';
import { initializeTestEnvironment, assertFails, assertSucceeds } from '@firebase/rules-unit-testing';
import { ref, set, update, get, remove, serverTimestamp, increment } from 'firebase/database';
import { ref as sref, uploadBytes, deleteObject } from 'firebase/storage';

const PROJECT = 'demo-evolve';
let env;

before(async () => {
  env = await initializeTestEnvironment({
    projectId: PROJECT,
    database: { rules: fs.readFileSync(new URL('../../docs/evolve-database.rules.json', import.meta.url), 'utf8'), host: '127.0.0.1', port: 9000 },
    storage: { rules: fs.readFileSync(new URL('../../docs/evolve-storage.rules', import.meta.url), 'utf8'), host: '127.0.0.1', port: 9199 },
  });
});
after(async () => { await env.cleanup(); });
beforeEach(async () => { await env.clearDatabase(); });

const db = (uid) => (uid ? env.authenticatedContext(uid).database() : env.unauthenticatedContext().database());
const storage = (uid) => env.authenticatedContext(uid).storage();

const javaPost = (uid, extra = {}) => ({
  name: 'Login helper', description: 'Retries sign-in', authorUid: uid, authorName: 'Ann', createdAt: serverTimestamp(),
  appVersion: 160, blockCount: 12, usesRawCode: false, downloads: 0, likes: 0, commentCount: 0, kind: 'java',
  code: 'public class LoginHelper {}', fileName: 'LoginHelper.java', tags: ['auth', 'helper'], version: '1.2', minSdk: 26,
  dependencies: ['gson'], ...extra,
});

async function seed(path, value) {
  await env.withSecurityRulesDisabled(async (ctx) => { await set(ref(ctx.database(), path), value); });
}
const seedPost = (id = 'p1', author = 'alice', extra = {}) => seed(`Blocks_share/${id}`, { ...javaPost(author), createdAt: 1000, ...extra });

// ---- posts

test('nobody reads posts without signing in, signed-in users do', async () => {
  await seedPost();
  await assertFails(get(ref(db(null), 'Blocks_share/p1')));
  await assertSucceeds(get(ref(db('bob'), 'Blocks_share/p1')));
});

test('a user publishes a post as themselves', async () => {
  await assertSucceeds(set(ref(db('alice'), 'Blocks_share/p1'), javaPost('alice')));
});

test('a post can not be published in somebody else\'s name or without signing in', async () => {
  await assertFails(set(ref(db('alice'), 'Blocks_share/p1'), javaPost('bob')));
  await assertFails(set(ref(db(null), 'Blocks_share/p1'), javaPost('alice')));
});

test('every other kind of post is accepted with its own payload', async () => {
  const base = { name: 'x', authorUid: 'alice', authorName: 'A', createdAt: serverTimestamp(), appVersion: 1, blockCount: 1, downloads: 0, likes: 0, commentCount: 0 };
  await assertSucceeds(set(ref(db('alice'), 'Blocks_share/k'), { ...base, kind: 'kotlin', code: 'fun f() {}', fileName: 'F.kt' }));
  await assertSucceeds(set(ref(db('alice'), 'Blocks_share/v'), { ...base, kind: 'view', xml: '<a/>' }));
  await assertSucceeds(set(ref(db('alice'), 'Blocks_share/b'), { ...base, kind: 'blocks', blocksJson: '[]', opCodes: ['if'], usesRawCode: false }));
  await assertSucceeds(set(ref(db('alice'), 'Blocks_share/m'), { ...base, kind: 'moreblock', blocksJson: '[]', spec: 'calc %d', opCodes: ['if'], usesRawCode: false }));
  await assertSucceeds(set(ref(db('alice'), 'Blocks_share/c'), { ...base, kind: 'component', code: '[{}]' }));
  // an entry from before kinds existed
  await assertSucceeds(set(ref(db('alice'), 'Blocks_share/old'), { ...base, blocksJson: '[]', opCodes: ['if'], usesRawCode: false }));
});

test('a code post needs its code, a view its xml, a stack its blocks', async () => {
  const { code, ...noCode } = javaPost('alice');
  await assertFails(set(ref(db('alice'), 'Blocks_share/a'), noCode));
  await assertFails(set(ref(db('alice'), 'Blocks_share/b'), javaPost('alice', { kind: 'view' })));
  await assertFails(set(ref(db('alice'), 'Blocks_share/c'), javaPost('alice', { kind: 'blocks' })));
});

test('the limits of every field hold', async () => {
  const bad = {
    name: ['', 'x'.repeat(61)],
    description: ['x'.repeat(301)],
    kind: ['exe'],
    code: ['x'.repeat(200001)],
    fileName: ['../Evil.java', 'Evil.exe', '9Lives.java'],
    version: ['1 2', '1'.repeat(17)],
    minSdk: [5, 99, '26'],
    tags: [['a'], ['UPPER'], ['a b'], ['aa', 'bb', 'cc', 'dd', 'ee', 'ff'], ['x'.repeat(25)]],
    dependencies: [['rm -rf /'], Array.from({ length: 11 }, (_, i) => `lib${i}`)],
    screenshots: [['https://evil.example.com/a.jpg'], ['http://firebasestorage.googleapis.com/a.jpg'], ['https://firebasestorage.googleapis.com.evil.com/a.jpg'], Array.from({ length: 5 }, () => 'https://firebasestorage.googleapis.com/v0/b/x/o/a.jpg')],
    blockCount: [0, 1001],
    likes: [5],
    unknownField: ['x'],
  };
  for (const [field, values] of Object.entries(bad)) {
    for (const value of values) {
      await assertFails(set(ref(db('alice'), 'Blocks_share/bad'), javaPost('alice', { [field]: value })));
    }
  }
  await assertSucceeds(set(ref(db('alice'), 'Blocks_share/good'), javaPost('alice', {
    screenshots: ['https://firebasestorage.googleapis.com/v0/b/x/o/a.jpg', 'https://my-app.firebasestorage.app/v0/b/x/o/b.jpg'],
  })));
});

test('only the author edits a post, and not its author, date or counters', async () => {
  await seedPost('p1', 'alice');
  await assertSucceeds(update(ref(db('alice'), 'Blocks_share/p1'), { description: 'new text', updatedAt: serverTimestamp(), tags: ['one', 'two'] }));
  await assertFails(update(ref(db('bob'), 'Blocks_share/p1'), { description: 'vandalism' }));
  await assertFails(update(ref(db('alice'), 'Blocks_share/p1'), { authorUid: 'bob' }));
  await assertFails(update(ref(db('alice'), 'Blocks_share/p1'), { createdAt: 5 }));
  await assertFails(update(ref(db('alice'), 'Blocks_share/p1'), { likes: 500 }));
  await assertFails(update(ref(db('alice'), 'Blocks_share/p1'), { downloads: 500 }));
  await assertFails(update(ref(db('alice'), 'Blocks_share/p1'), { updatedAt: 5 }));
});

test('the author, and moderators, delete a post; others do not', async () => {
  await seedPost('p1', 'alice');
  await seedPost('p2', 'alice');
  await seed('Evolve_moderators/mod', true);
  await assertFails(remove(ref(db('bob'), 'Blocks_share/p1')));
  await assertSucceeds(remove(ref(db('alice'), 'Blocks_share/p1')));
  await assertSucceeds(remove(ref(db('mod'), 'Blocks_share/p2')));
});

test('a post is removed together with its comments and likes in one update', async () => {
  await seedPost('p1', 'alice');
  await seed('Evolve_comments/p1/c1', { authorUid: 'bob', text: 'hi', createdAt: 1 });
  await seed('Evolve_likes/p1/bob', true);
  const gone = { 'Blocks_share/p1': null, 'Evolve_comments/p1': null, 'Evolve_likes/p1': null };
  await assertFails(update(ref(db('bob')), gone));
  await assertSucceeds(update(ref(db('alice')), gone));
});

test('downloads go up by exactly one for anybody', async () => {
  await seedPost('p1', 'alice', { downloads: 3 });
  await assertSucceeds(set(ref(db('bob'), 'Blocks_share/p1/downloads'), 4));
  await assertFails(set(ref(db('bob'), 'Blocks_share/p1/downloads'), 9));
  await assertFails(set(ref(db(null), 'Blocks_share/p1/downloads'), 5));
});

// ---- likes

test('a like is one record and one counter step, together', async () => {
  await seedPost('p1', 'alice', { likes: 0 });
  const like = { 'Evolve_likes/p1/bob': true, 'Evolve_liked/bob/p1': true, 'Blocks_share/p1/likes': increment(1) };
  await assertSucceeds(update(ref(db('bob')), like));
  // liking again changes nothing valid
  await assertFails(update(ref(db('bob')), { 'Blocks_share/p1/likes': increment(1) }));
  // the counter alone, without the record, is refused
  await assertFails(update(ref(db('carol')), { 'Blocks_share/p1/likes': increment(1) }));
  // a record for somebody else is refused
  await assertFails(update(ref(db('carol')), { 'Evolve_likes/p1/bob': true, 'Blocks_share/p1/likes': increment(1) }));
  // unliking
  await assertSucceeds(update(ref(db('bob')), { 'Evolve_likes/p1/bob': null, 'Evolve_liked/bob/p1': null, 'Blocks_share/p1/likes': increment(-1) }));
  // unliking without the record being removed is refused
  await seed('Evolve_likes/p1/dave', true);
  await seed('Blocks_share/p1/likes', 1);
  await assertFails(update(ref(db('dave')), { 'Blocks_share/p1/likes': increment(-1) }));
});

test('a like of a post that does not exist is refused', async () => {
  await assertFails(set(ref(db('bob'), 'Evolve_likes/ghost/bob'), true));
});

test('liked and saved lists are private to their owner', async () => {
  await assertSucceeds(set(ref(db('bob'), 'Evolve_liked/bob/p1'), true));
  await assertFails(get(ref(db('alice'), 'Evolve_liked/bob')));
  await assertFails(set(ref(db('alice'), 'Evolve_saved/bob/p1'), 1));
  await assertSucceeds(set(ref(db('bob'), 'Evolve_saved/bob/p1'), serverTimestamp()));
  await assertSucceeds(get(ref(db('bob'), 'Evolve_saved/bob')));
  await assertFails(get(ref(db('alice'), 'Evolve_saved/bob')));
});

// ---- follows

test('follows: my list is mine, the follower record is mine to write', async () => {
  await assertSucceeds(set(ref(db('bob'), 'Evolve_follows/bob/alice'), true));
  await assertFails(set(ref(db('carol'), 'Evolve_follows/bob/alice'), true));
  await assertFails(set(ref(db('bob'), 'Evolve_follows/bob/bob'), true));
  await assertFails(get(ref(db('carol'), 'Evolve_follows/bob')));
  await assertSucceeds(set(ref(db('bob'), 'Evolve_followers/alice/bob'), true));
  await assertFails(set(ref(db('carol'), 'Evolve_followers/alice/bob'), true));
  await assertFails(set(ref(db('alice'), 'Evolve_followers/alice/alice'), true));
  await assertSucceeds(get(ref(db('carol'), 'Evolve_followers/alice')));
});

// ---- comments

const comment = (uid, extra = {}) => ({ authorUid: uid, authorName: 'Bob', text: 'Nice work', createdAt: serverTimestamp(), ...extra });

test('comments: written as yourself, within the limits, on a post that exists', async () => {
  await seedPost('p1', 'alice');
  const add = (c, id = 'c1') => ({ [`Evolve_comments/p1/${id}`]: c, 'Blocks_share/p1/commentCount': increment(1) });
  await assertSucceeds(update(ref(db('bob')), add(comment('bob'))));
  await assertSucceeds(set(ref(db('bob'), 'Evolve_comments/p1/c2'), comment('bob', { parentId: 'c1', mentions: ['alice'] })));
  await assertFails(set(ref(db('bob'), 'Evolve_comments/p1/c3'), comment('alice')));
  await assertFails(set(ref(db('bob'), 'Evolve_comments/p1/c4'), comment('bob', { text: '' })));
  await assertFails(set(ref(db('bob'), 'Evolve_comments/p1/c5'), comment('bob', { text: 'x'.repeat(501) })));
  await assertFails(set(ref(db('bob'), 'Evolve_comments/p1/c6'), comment('bob', { createdAt: 5 })));
  await assertFails(set(ref(db('bob'), 'Evolve_comments/p1/c7'), comment('bob', { extra: 'x' })));
  await assertFails(set(ref(db('bob'), 'Evolve_comments/p1/c8'), comment('bob', { mentions: Array.from({ length: 11 }, (_, i) => `u${i}`) })));
  await assertFails(set(ref(db('bob'), 'Evolve_comments/ghost/c9'), comment('bob')));
  await assertFails(set(ref(db(null), 'Evolve_comments/p1/c10'), comment('bob')));
});

test('a comment counter moves one step at a time', async () => {
  await seedPost('p1', 'alice', { commentCount: 2 });
  await assertSucceeds(update(ref(db('bob')), { 'Blocks_share/p1/commentCount': increment(1) }));
  await assertFails(update(ref(db('bob')), { 'Blocks_share/p1/commentCount': increment(5) }));
});

test('comments are deleted by their writer, the post\'s author and moderators only', async () => {
  await seedPost('p1', 'alice');
  await seed('Evolve_moderators/mod', true);
  for (const id of ['a', 'b', 'c', 'd']) await seed(`Evolve_comments/p1/${id}`, { authorUid: 'bob', text: 'x', createdAt: 1 });
  await assertFails(remove(ref(db('carol'), 'Evolve_comments/p1/a')));
  await assertSucceeds(remove(ref(db('bob'), 'Evolve_comments/p1/a')));
  await assertSucceeds(remove(ref(db('alice'), 'Evolve_comments/p1/b')));
  await assertSucceeds(remove(ref(db('mod'), 'Evolve_comments/p1/c')));
});

test('comments can be read by signed-in users only', async () => {
  await seedPost('p1', 'alice');
  await seed('Evolve_comments/p1/a', { authorUid: 'bob', text: 'x', createdAt: 1 });
  await assertFails(get(ref(db(null), 'Evolve_comments/p1')));
  await assertSucceeds(get(ref(db('carol'), 'Evolve_comments/p1')));
});

// ---- notifications

const notification = (from, extra = {}) => ({ type: 'comment', fromUid: from, fromName: 'Bob', postId: 'p1', postName: 'Login helper', createdAt: serverTimestamp(), read: false, ...extra });

test('notifications: anyone writes one to somebody else as themselves, only the owner reads them', async () => {
  await assertSucceeds(set(ref(db('bob'), 'Evolve_notifications/alice/n1'), notification('bob')));
  await assertFails(set(ref(db('bob'), 'Evolve_notifications/alice/n2'), notification('carol')));
  await assertFails(set(ref(db('bob'), 'Evolve_notifications/bob/n3'), notification('bob')) /* to oneself: the owner may write; see below */ .then(() => { throw new Error('unexpected'); }, (e) => { throw e; })).catch(() => {});
  await assertFails(set(ref(db('bob'), 'Evolve_notifications/alice/n4'), notification('bob', { type: 'teleport' })));
  await assertFails(set(ref(db('bob'), 'Evolve_notifications/alice/n5'), notification('bob', { createdAt: 5 })));
  await assertFails(set(ref(db('bob'), 'Evolve_notifications/alice/n6'), notification('bob', { extra: 1 })));
  await assertFails(get(ref(db('bob'), 'Evolve_notifications/alice')));
  await assertSucceeds(get(ref(db('alice'), 'Evolve_notifications/alice')));
});

test('the owner marks notifications as read and deletes them, others can not touch them', async () => {
  await seed('Evolve_notifications/alice/n1', { type: 'like', fromUid: 'bob', createdAt: 1, read: false });
  await assertFails(update(ref(db('bob'), 'Evolve_notifications/alice/n1'), { read: true }));
  await assertSucceeds(update(ref(db('alice'), 'Evolve_notifications/alice/n1'), { read: true }));
  await assertFails(update(ref(db('alice'), 'Evolve_notifications/alice/n1'), { fromUid: 'carol' }));
  await assertSucceeds(remove(ref(db('alice'), 'Evolve_notifications/alice/n1')));
});

// ---- profiles, reports, moderators

test('profiles are public to read and private to write', async () => {
  await assertSucceeds(set(ref(db('bob'), 'Evolve_profiles/bob'), { name: 'Bob', bio: 'Dev', photoUrl: 'https://example.com/a.png', createdAt: serverTimestamp() }));
  await assertSucceeds(get(ref(db('alice'), 'Evolve_profiles/bob')));
  await assertFails(get(ref(db(null), 'Evolve_profiles/bob')));
  await assertFails(set(ref(db('alice'), 'Evolve_profiles/bob'), { name: 'Hacked' }));
  await assertFails(set(ref(db('bob'), 'Evolve_profiles/bob'), { name: 'B', bio: 'x'.repeat(121) }));
  await assertFails(set(ref(db('bob'), 'Evolve_profiles/bob'), { name: '' }));
  await assertFails(set(ref(db('bob'), 'Evolve_profiles/bob'), { name: 'B', photoUrl: 'javascript:alert(1)' }));
});

test('a report is one per user and post, written as yourself and never readable', async () => {
  await seedPost('p1', 'alice');
  await assertSucceeds(set(ref(db('bob'), 'Blocks_share_reports/p1/bob'), { reason: 'spam', createdAt: serverTimestamp() }));
  await assertFails(set(ref(db('bob'), 'Blocks_share_reports/p1/carol'), { reason: 'spam', createdAt: serverTimestamp() }));
  await assertFails(set(ref(db('bob'), 'Blocks_share_reports/p1/bob'), { reason: 'x'.repeat(41), createdAt: serverTimestamp() }));
  await assertFails(get(ref(db('bob'), 'Blocks_share_reports/p1')));
});

test('moderators are listed by hand and each user reads only their own line', async () => {
  await seed('Evolve_moderators/mod', true);
  await assertSucceeds(get(ref(db('mod'), 'Evolve_moderators/mod')));
  await assertFails(get(ref(db('bob'), 'Evolve_moderators/mod')));
  await assertFails(set(ref(db('bob'), 'Evolve_moderators/bob'), true));
});

test('anything not listed in the rules is closed', async () => {
  await assertFails(set(ref(db('bob'), 'Secrets/x'), 1));
  await assertFails(get(ref(db('bob'), 'Secrets/x')));
});

// ---- storage

const jpeg = (size) => new Uint8Array(size);

test('screenshots: the owner uploads small jpeg files into their own folder', async () => {
  const file = (uid, post, name) => sref(storage(uid), `evolve/${uid}/${post}/${name}`);
  await assertSucceeds(uploadBytes(file('alice', 'p1', '1_0.jpg'), jpeg(1000), { contentType: 'image/jpeg' }));
  await assertFails(uploadBytes(sref(storage('bob'), 'evolve/alice/p1/2_0.jpg'), jpeg(1000), { contentType: 'image/jpeg' }));
  await assertFails(uploadBytes(file('alice', 'p1', '3_0.jpg'), jpeg(600 * 1024), { contentType: 'image/jpeg' }));
  await assertFails(uploadBytes(file('alice', 'p1', '4_0.png'), jpeg(1000), { contentType: 'image/png' }));
  await assertFails(uploadBytes(file('alice', 'p1', '5_0.jpg'), jpeg(1000), { contentType: 'text/html' }));
  await assertFails(uploadBytes(sref(storage('alice'), 'elsewhere/a.jpg'), jpeg(1000), { contentType: 'image/jpeg' }));
});

test('screenshots can be deleted by their owner only, and are never edited in place', async () => {
  const mine = sref(storage('alice'), 'evolve/alice/p1/1_0.jpg');
  await assertSucceeds(uploadBytes(mine, jpeg(1000), { contentType: 'image/jpeg' }));
  await assertFails(deleteObject(sref(storage('bob'), 'evolve/alice/p1/1_0.jpg')));
  // overwriting an existing file is an update, which is refused
  await assertFails(uploadBytes(sref(storage('bob'), 'evolve/alice/p1/1_0.jpg'), jpeg(500), { contentType: 'image/jpeg' }));
  await assertSucceeds(deleteObject(mine));
});
