package com.mythosforge.genre;

import com.mythosforge.genre.dto.GenreRecommendRequest;
import com.mythosforge.genre.dto.GenreRecommendResponse;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/projects/{projectId}/genre")
public class GenreController {

    private final GenreService genreService;

    public GenreController(GenreService genreService) {
        this.genreService = genreService;
    }

    @PostMapping("/recommend")
    public GenreRecommendResponse recommend(
            @PathVariable String projectId,
            @Valid @RequestBody GenreRecommendRequest body
    ) {
        return genreService.recommend(projectId, body);
    }
}
