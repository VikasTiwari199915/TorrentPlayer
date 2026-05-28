package com.vikas.torrentplayer.api.models.github;

import com.google.gson.annotations.SerializedName;

import lombok.Data;

/**
 * Retrofit model for GitHub API
 * @author Vikas Tiwari
 */
@Data
public class Author {

   @SerializedName("login")
   String login;

   @SerializedName("id")
   int id;

   @SerializedName("node_id")
   String nodeId;

   @SerializedName("avatar_url")
   String avatarUrl;

   @SerializedName("gravatar_id")
   String gravatarId;

   @SerializedName("url")
   String url;

   @SerializedName("html_url")
   String htmlUrl;

   @SerializedName("followers_url")
   String followersUrl;

   @SerializedName("following_url")
   String followingUrl;

   @SerializedName("gists_url")
   String gistsUrl;

   @SerializedName("starred_url")
   String starredUrl;

   @SerializedName("subscriptions_url")
   String subscriptionsUrl;

   @SerializedName("organizations_url")
   String organizationsUrl;

   @SerializedName("repos_url")
   String reposUrl;

   @SerializedName("events_url")
   String eventsUrl;

   @SerializedName("received_events_url")
   String receivedEventsUrl;

   @SerializedName("type")
   String type;

   @SerializedName("user_view_type")
   String userViewType;

   @SerializedName("site_admin")
   boolean siteAdmin;

}