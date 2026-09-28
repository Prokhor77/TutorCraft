package com.tutorcraft.core.communication.forum.web;

import com.tutorcraft.core.communication.forum.application.DiscussionService;
import com.tutorcraft.core.communication.forum.application.ForumViews.DiscussionDetailView;
import com.tutorcraft.core.communication.forum.application.ForumViews.DiscussionView;
import com.tutorcraft.core.communication.forum.application.ForumViews.PostView;
import com.tutorcraft.core.communication.forum.application.PostService;
import com.tutorcraft.core.shared.api.PageQuery;
import com.tutorcraft.core.shared.api.PageResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Форумы (контракт §11). */
@RestController
@RequestMapping("/api/v1")
class ForumController {

    private static final int MAX_TITLE = 255;
    private static final int MAX_MENTIONS = 20;

    private final DiscussionService discussions;
    private final PostService posts;

    ForumController(DiscussionService discussions, PostService posts) {
        this.discussions = discussions;
        this.posts = posts;
    }

    @GetMapping("/items/{itemId}/discussions")
    PageResponse<DiscussionView> list(@PathVariable UUID itemId, @RequestParam(required = false) String cursor,
                                      @RequestParam(required = false) Integer limit) {
        return discussions.list(itemId, PageQuery.of(cursor, limit));
    }

    @PostMapping("/items/{itemId}/discussions")
    @ResponseStatus(HttpStatus.CREATED)
    DiscussionView create(@PathVariable UUID itemId, @Valid @RequestBody DiscussionRequest request) {
        return discussions.create(itemId, request.title(), request.body(), request.mentions());
    }

    @GetMapping("/discussions/{id}")
    DiscussionDetailView get(@PathVariable UUID id) {
        return discussions.get(id);
    }

    @PostMapping("/discussions/{id}/posts")
    @ResponseStatus(HttpStatus.CREATED)
    PostView reply(@PathVariable UUID id, @Valid @RequestBody PostRequest request) {
        return posts.reply(id, request.parentId(), request.body(), request.mentions());
    }

    @PatchMapping("/posts/{id}")
    PostView edit(@PathVariable UUID id, @Valid @RequestBody EditRequest request) {
        return posts.edit(id, request.body());
    }

    @DeleteMapping("/posts/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void delete(@PathVariable UUID id) {
        posts.delete(id);
    }

    @PostMapping("/posts/{id}/hide")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void hide(@PathVariable UUID id) {
        posts.setHidden(id, true);
    }

    @DeleteMapping("/posts/{id}/hide")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void unhide(@PathVariable UUID id) {
        posts.setHidden(id, false);
    }

    @PostMapping("/discussions/{id}/pin")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void pin(@PathVariable UUID id) {
        discussions.setPinned(id, true);
    }

    @DeleteMapping("/discussions/{id}/pin")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void unpin(@PathVariable UUID id) {
        discussions.setPinned(id, false);
    }

    @PostMapping("/discussions/{id}/lock")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void lock(@PathVariable UUID id) {
        discussions.setLocked(id, true);
    }

    @DeleteMapping("/discussions/{id}/lock")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void unlock(@PathVariable UUID id) {
        discussions.setLocked(id, false);
    }

    @PostMapping("/discussions/{id}/subscribe")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void subscribe(@PathVariable UUID id) {
        discussions.setSubscribed(id, true);
    }

    @DeleteMapping("/discussions/{id}/subscribe")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void unsubscribe(@PathVariable UUID id) {
        discussions.setSubscribed(id, false);
    }

    @PostMapping("/discussions/{id}/read")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void read(@PathVariable UUID id) {
        discussions.markRead(id);
    }

    record DiscussionRequest(@NotBlank @Size(max = MAX_TITLE) String title, @NotNull Map<String, Object> body,
                             @Size(max = MAX_MENTIONS) List<UUID> mentions) {
    }

    record PostRequest(UUID parentId, @NotNull Map<String, Object> body, @Size(max = MAX_MENTIONS) List<UUID> mentions) {
    }

    record EditRequest(@NotNull Map<String, Object> body) {
    }
}
