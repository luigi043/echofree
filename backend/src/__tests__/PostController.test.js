const Post = require('../models/Post');
const { createPost, getPosts } = require('../controllers/PostController');

function response() {
    return { status: jest.fn().mockReturnThis(), json: jest.fn() };
}

afterEach(() => jest.restoreAllMocks());

test('successful publish returns the numeric timestamp required by the Android client', async () => {
    const createdAt = new Date('2026-01-01T12:00:00.000Z');
    jest.spyOn(Post, 'create').mockImplementation(async draft => new Post({ ...draft, createdAt }));
    const res = response();

    await createPost({ body: { content: ' A test post ', tags: ['Writing', 'writing'] } }, res);

    expect(res.status).toHaveBeenCalledWith(201);
    expect(res.json).toHaveBeenCalledWith({
        id: expect.any(String),
        content: 'A test post',
        tags: ['writing'],
        authorName: 'Anonymous',
        createdAt: createdAt.getTime()
    });
});

test.each([{ content: 42 }, { content: 'Draft', tags: 'writing' }])(
    'returns 400 for a malformed draft: %j',
    async body => {
        const insert = jest.spyOn(Post, 'create');
        const res = response();
        await createPost({ body }, res);
        expect(res.status).toHaveBeenCalledWith(400);
        expect(insert).not.toHaveBeenCalled();
    }
);

test('returns 400 for a repeated tags query parameter', async () => {
    const find = jest.spyOn(Post, 'find');
    const res = response();
    await getPosts({ query: { tags: ['writing', 'design'] } }, res);
    expect(res.status).toHaveBeenCalledWith(400);
    expect(find).not.toHaveBeenCalled();
});
