package com.molean.isletopianetwork;

import com.google.gson.Gson;
import org.bukkit.Bukkit;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.Function;

public class Client {

    private Thread thread;

    private ServerSocket serverSocket;

    private final Function<Request, Response> requstHandler;

    public Client(Function<Request, Response> requstHandler) {
        this.requstHandler = requstHandler;
    }

    public static Response send(Request request) {
        return send("localhost", 6129, request);
    }

    public static Response send(String hostname, int port, Request request) {
        Response response = null;
        try (Socket socket = new Socket(hostname, port)) {
            socket.setSoTimeout(100);
            OutputStream outputStream = socket.getOutputStream();
            outputStream.write(new Gson().toJson(request).getBytes());
            socket.shutdownOutput();
            InputStream inputStream = socket.getInputStream();
            byte[] bytes = inputStream.readAllBytes();
            String responseString = new String(bytes);
            response = new Gson().fromJson(responseString, Response.class);
        } catch (SocketTimeoutException exception) {
            Bukkit.getLogger().warning("Response timeout.");
        } catch (IOException exception) {
            exception.printStackTrace();
        }
        return response;
    }

    public boolean register(String name) {
        try {
            serverSocket = new ServerSocket(0);
        } catch (IOException exception) {
            return false;
        }
        Request request = new Request();
        thread = new Thread(this::listen);
        thread.start();
        request.setType("register");
        request.setTarget("IsletopiaNetwork");
        request.getData().put("hostname", "localhost");
        request.getData().put("name", name);
        request.getData().put("port", serverSocket.getLocalPort() + "");
        Response response = send(request);
        return response != null && response.getStatus().equalsIgnoreCase("successfully");
    }

    public static boolean unregister(String name) {
        Request request = new Request();
        request.setType("unregister");
        request.setTarget("IsletopiaNetwork");
        request.getData().put("name", name);
        Response response = send(request);
        return response != null && response.getStatus().equalsIgnoreCase("successfully");
    }

    public static List<String> getRegistrations() {
        Request request = new Request();
        request.setType("getRegistrations");
        request.setTarget("IsletopiaNetwork");
        Response response = send(request);
        String rawRegistrations = response.get("return");
        if (rawRegistrations == null || "".equalsIgnoreCase(rawRegistrations.trim())) {
            return new ArrayList<>();
        } else {
            return Arrays.asList(rawRegistrations.split(","));
        }
    }

    private void listen() {
        while (true) {
            try {
                Socket socket = serverSocket.accept();
                socket.setSoTimeout(100);
                new Thread(() -> {
                    try (socket) {
                        InputStream inputStream = socket.getInputStream();
                        String requestString = new String(inputStream.readAllBytes());
                        Request request = new Gson().fromJson(requestString, Request.class);
                        Response response = requstHandler.apply(request);
                        String responseString = new Gson().toJson(response);
                        OutputStream outputStream = socket.getOutputStream();
                        outputStream.write(responseString.getBytes());
                    } catch (IOException exception) {
                        exception.printStackTrace();
                    }
                }).start();
            } catch (IOException exception) {
                exception.printStackTrace();
            }
        }
    }
}
