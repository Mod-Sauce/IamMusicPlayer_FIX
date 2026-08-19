package dev.felnull.imp.server.webdav;

import net.minecraft.nbt.CompoundTag;
import org.modsauce.otyacraftenginerenewed.server.level.TagSerializable;

public class ServerWebDAVProfile implements TagSerializable {
    private String baseUrl = "";
    private String rootPath = "";
    private String username = "";
    private String encryptedPassword = "";

    public ServerWebDAVProfile() {
    }

    public ServerWebDAVProfile(String baseUrl, String rootPath, String username, String encryptedPassword) {
        this.baseUrl = baseUrl;
        this.rootPath = rootPath;
        this.username = username;
        this.encryptedPassword = encryptedPassword;
    }

    @Override
    public void save(CompoundTag tag) {
        tag.putString("BaseUrl", baseUrl);
        tag.putString("RootPath", rootPath);
        tag.putString("Username", username);
        tag.putString("EncryptedPassword", encryptedPassword);
    }

    @Override
    public void load(CompoundTag tag) {
        this.baseUrl = tag.getString("BaseUrl");
        this.rootPath = tag.getString("RootPath");
        this.username = tag.getString("Username");
        this.encryptedPassword = tag.getString("EncryptedPassword");
    }

    public String getBaseUrl() {
        return baseUrl;
    }

    public String getRootPath() {
        return rootPath;
    }

    public String getUsername() {
        return username;
    }

    public String getEncryptedPassword() {
        return encryptedPassword;
    }

    public String getPassword() {
        return ServerWebDAVCrypto.decrypt(encryptedPassword);
    }
}
