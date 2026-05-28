package com.vikas.torrentplayer.api.models.github;
import java.util.Date;
import java.util.List;

import com.google.gson.annotations.SerializedName;

import lombok.Data;

/**
 * Retrofit model for GitHub API
 * @author Vikas Tiwari
 */
@Data
public class GithubRelease {

   @SerializedName("url")
   String url;

   @SerializedName("assets_url")
   String assetsUrl;

   @SerializedName("upload_url")
   String uploadUrl;

   @SerializedName("html_url")
   String htmlUrl;

   @SerializedName("id")
   int id;

   @SerializedName("author")
   Author author;

   @SerializedName("node_id")
   String nodeId;

   @SerializedName("tag_name")
   String tagName;

   @SerializedName("target_commitish")
   String targetCommitish;

   @SerializedName("name")
   String name;

   @SerializedName("draft")
   boolean draft;

   @SerializedName("immutable")
   boolean immutable;

   @SerializedName("prerelease")
   boolean prerelease;

   @SerializedName("created_at")
   Date createdAt;

   @SerializedName("published_at")
   Date publishedAt;

   @SerializedName("assets")
   List<ReleaseAssets> assets;

   @SerializedName("tarball_url")
   String tarballUrl;

   @SerializedName("zipball_url")
   String zipballUrl;

   @SerializedName("body")
   String body;
}