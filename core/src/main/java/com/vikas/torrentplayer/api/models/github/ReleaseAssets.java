package com.vikas.torrentplayer.api.models.github;
import java.util.Date;

import com.google.gson.annotations.SerializedName;

import lombok.Data;

/**
 * Retrofit model for GitHub API
 * @author Vikas Tiwari
 */
@Data
public class ReleaseAssets {

   @SerializedName("url")
   String url;

   @SerializedName("id")
   int id;

   @SerializedName("node_id")
   String nodeId;

   @SerializedName("name")
   String name;

   @SerializedName("label")
   String label;

   @SerializedName("uploader")
   Uploader uploader;

   @SerializedName("content_type")
   String contentType;

   @SerializedName("state")
   String state;

   @SerializedName("size")
   int size;

   @SerializedName("digest")
   String digest;

   @SerializedName("download_count")
   int downloadCount;

   @SerializedName("created_at")
   Date createdAt;

   @SerializedName("updated_at")
   Date updatedAt;

   @SerializedName("browser_download_url")
   String browserDownloadUrl;
    
}