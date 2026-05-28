package com.vikas.torrentplayer.api;

import com.vikas.torrentplayer.api.models.github.GithubRelease;

import java.util.List;

import retrofit2.Call;
import retrofit2.http.GET;
import retrofit2.http.Path;

/**
 * Retrofit interface for the GitHub REST API. Used by the in-app auto-updater
 * to read the releases list of a configurable {@code owner/repo}.
 *
 * @author Vikas Tiwari
 */
public interface GitHubApiService {

    /** Returns the most-recent-first list of releases for the given repo. */
    @GET("repos/{owner}/{repo}/releases")
    Call<List<GithubRelease>> getReleases(
            @Path("owner") String owner,
            @Path("repo") String repo);
}
