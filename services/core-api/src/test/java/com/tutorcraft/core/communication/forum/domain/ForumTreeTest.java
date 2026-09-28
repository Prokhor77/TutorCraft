package com.tutorcraft.core.communication.forum.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Дерево ответов глубиной ≤ 3, дальше — плоско (FR-FORUM-01). */
class ForumTreeTest {

    private static final Instant NOW = Instant.parse("2026-09-27T10:00:00Z");
    private static final UUID AUTHOR = new UUID(0, 99);

    private static ForumPost post(int n, ForumPost parent) {
        UUID parentId = parent == null ? null : parent.id();
        int depth = parent == null ? 0 : ReplyPlacement.under(parent).depth();
        UUID effectiveParent = parent == null ? null : ReplyPlacement.under(parent).parentId();
        return new ForumPost(new UUID(0, n), new UUID(1, 1), new UUID(1, 2), new UUID(1, 3), new UUID(1, 4), effectiveParent,
                depth, AUTHOR, Map.of(), false, NOW.plusSeconds(n), null);
    }

    @Test
    void repliesNestUntilMaxDepthThenAttachAsSiblings() {
        ForumPost root = post(1, null);
        ForumPost level1 = post(2, root);
        ForumPost level2 = post(3, level1);
        ForumPost level3 = post(4, level2);
        ReplyPlacement tooDeep = ReplyPlacement.under(level3);
        assertThat(List.of(level1.depth(), level2.depth(), level3.depth())).containsExactly(1, 2, 3);
        assertThat(tooDeep.depth()).isEqualTo(ReplyPlacement.MAX_DEPTH);
        assertThat(tooDeep.parentId()).isEqualTo(level2.id());
    }

    @Test
    void treeIsBuiltInCreationOrder() {
        ForumPost root = post(1, null);
        ForumPost first = post(2, root);
        ForumPost second = post(3, root);
        ForumPost nested = post(4, first);
        List<String> rendered = new ArrayList<>();
        List<Node> roots = PostTree.build(List.of(root, first, second, nested), Node::new);
        roots.forEach(node -> node.render("", rendered));
        assertThat(rendered).containsExactly("1", "-2", "--4", "-3");
    }

    @Test
    void orphanedPostsBecomeRoots() {
        ForumPost root = post(1, null);
        ForumPost reply = post(2, root);
        assertThat(PostTree.build(List.of(reply), Node::new)).hasSize(1);
    }

    private record Node(ForumPost post, List<Node> children) {

        void render(String prefix, List<String> out) {
            out.add(prefix + post.id().getLeastSignificantBits());
            children.forEach(child -> child.render(prefix + "-", out));
        }
    }
}
