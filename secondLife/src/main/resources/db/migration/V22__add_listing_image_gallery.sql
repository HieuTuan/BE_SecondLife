CREATE TABLE post_images (
    post_id UUID NOT NULL REFERENCES posts(id) ON DELETE CASCADE,
    image_position INTEGER NOT NULL CHECK(image_position BETWEEN 0 AND 5),
    image_url TEXT NOT NULL,
    image_fingerprint VARCHAR(64),
    PRIMARY KEY(post_id, image_position)
);
