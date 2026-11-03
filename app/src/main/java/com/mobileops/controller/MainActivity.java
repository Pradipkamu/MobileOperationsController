package com.mobileops.controller;

import android.app.Activity;
import android.os.Bundle;
import android.content.Intent;
import android.net.Uri;
import android.provider.OpenableColumns;
import android.content.ContentResolver;
import android.graphics.Color;
import android.view.View;
import android.widget.*;
import android.text.InputType;
import android.util.Base64;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.KeyStore;
import java.security.MessageDigest;

public class MainActivity extends Activity {
    private static final int PICK_KEY = 4101;
    private static final String PREFS = "secure_server_config";
    private static final String KEY_ALIAS = "mobile_ops_ssh_v1";
    private static final String HOST = "137.23.40.188";
    private TextView keyState, pinState, overall;
    private EditText fingerprintInput;\n    private TextView diagnosticOutput;

    private int dp(float n) { return (int)(n * getResources().getDisplayMetrics().density + 0.5f); }
    private TextView label(String s, int size, boolean bold) {
        TextView t = new TextView(this); t.setText(s); t.setTextSize(size);
        t.setTextColor(Color.rgb(15,23,42));
        if (bold) t.setTypeface(null, 1);
        return t;
    }
    private void gap(LinearLayout p, int h) {
        View v = new View(this); p.addView(v, new LinearLayout.LayoutParams(1,dp(h)));
    }
    private LinearLayout card(LinearLayout parent) {
        LinearLayout c = new LinearLayout(this); c.setOrientation(1);
        c.setPadding(dp(16),dp(15),dp(16),dp(15)); c.setBackgroundColor(Color.WHITE);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1,-2);
        lp.bottomMargin=dp(12); parent.addView(c,lp); return c;
    }
    private Button button(String text, LinearLayout parent, Runnable action) {
        Button b = new Button(this); b.setText(text); parent.addView(b);
        b.setOnClickListener(v -> action.run()); return b;
    }
    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().setStatusBarColor(Color.rgb(244,247,251));
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR);
        ScrollView scroll=new ScrollView(this); scroll.setBackgroundColor(Color.rgb(244,247,251));
        LinearLayout root=new LinearLayout(this); root.setOrientation(1);
        root.setPadding(dp(18),dp(22),dp(18),dp(24)); scroll.addView(root);
        root.addView(label("MOBILE OPERATIONS",22,true));
        root.addView(label("Operations Controller • SSH setup",13,false));
        gap(root,18);
        LinearLayout conn=card(root);
        conn.addView(label("PRADIPWEB SERVER",12,true)); gap(conn,7);
        conn.addView(label(HOST+" • ubuntu • SSH port 22",15,true)); gap(conn,5);
        overall=label("NOT CONNECTED — credentials not verified",13,true); conn.addView(overall);
        LinearLayout credentials=card(root);
        credentials.addView(label("1. PRIVATE SSH KEY",17,true)); gap(credentials,6);
        credentials.addView(label("Choose your existing private key file. It will be encrypted with an Android Keystore key; the original file is not modified.",13,false));
        gap(credentials,8);
        keyState=label(hasEncryptedKey()?"Encrypted key stored on this phone":"No SSH key imported",13,true);
        credentials.addView(keyState);
        button("IMPORT PRIVATE KEY",credentials,()->{
            Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT);
            i.addCategory(Intent.CATEGORY_OPENABLE); i.setType("*/*");
            startActivityForResult(i,PICK_KEY);
        });
        LinearLayout trust=card(root);
        trust.addView(label("2. TRUSTED SERVER FINGERPRINT",17,true)); gap(trust,6);
        trust.addView(label("Enter the SHA256 fingerprint of Pradipweb's SSH host key, obtained independently from the server console or administrator. Do not accept an unverified fingerprint.",13,false));
        gap(trust,8);
        fingerprintInput=new EditText(this);
        fingerprintInput.setSingleLine(true);
        fingerprintInput.setHint("SHA256:...");
        fingerprintInput.setInputType(InputType.TYPE_CLASS_TEXT);
        trust.addView(fingerprintInput);
        pinState=label(hasPin()?"A fingerprint is saved on this phone":"No host fingerprint trusted yet",13,true);
        trust.addView(pinState);
        button("SAVE TRUSTED FINGERPRINT",trust,()->savePin());
        LinearLayout next=card(root);
        next.addView(label("3. CONNECTION",17,true)); gap(next,6);
        next.addView(label("SSH authentication and live monitoring will be enabled after the SSH transport library is integrated and the trusted fingerprint is confirmed. This build does not claim an SSH connection.",13,false));
        gap(next,8);
        next.addView(label("READ-ONLY POLICY • No server changes • No arbitrary shell commands",12,true));\n        gap(next,8);\n        diagnosticOutput=label("No server checks have been run.",13,false);\n        next.addView(diagnosticOutput);\n        button("TEST SSH & READ SERVER STATUS",next,()->runReadOnlyChecks());
        setContentView(scroll);
    }
    private byte[] decryptStoredKey() throws Exception {
        android.content.SharedPreferences prefs=getSharedPreferences(PREFS,0);
        String ivText=prefs.getString("key_iv",null);
        String encryptedText=prefs.getString("key_ciphertext",null);
        if(ivText==null || encryptedText==null) throw new IllegalStateException("Import the SSH key first");
        byte[] iv=Base64.decode(ivText,Base64.NO_WRAP);
        byte[] encrypted=Base64.decode(encryptedText,Base64.NO_WRAP);
        Cipher cipher=Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.DECRYPT_MODE,getOrCreateAesKey(),new GCMParameterSpec(128,iv));
        return cipher.doFinal(encrypted);
    }

    private void runReadOnlyChecks() {
        if(!hasEncryptedKey() || !hasPin()) {
            Toast.makeText(this,"Import the key and save the verified host fingerprint first",Toast.LENGTH_LONG).show();
            return;
        }
        diagnosticOutput.setText("Connecting securely…");
        overall.setText("CONNECTING — verifying host and authenticating");
        new Thread(() -> {
            byte[] privateKey=null;
            try {
                privateKey=decryptStoredKey();
                String host=PradipwebSshClient.run(privateKey,
                    getSharedPreferences(PREFS,0).getString("host_fingerprint",""),
                    PradipwebSshClient.Check.HOSTNAME);
                String docker=PradipwebSshClient.run(privateKey,
                    getSharedPreferences(PREFS,0).getString("host_fingerprint",""),
                    PradipwebSshClient.Check.DOCKER_STATUS);
                final String result="SSH authenticated • Host: "+host+"\n\nDocker containers:\n"+docker;
                runOnUiThread(() -> {
                    overall.setText("CONNECTED — host verified and key authenticated");
                    diagnosticOutput.setText(result);
                });
            } catch(Exception e) {
                final String message=e.getMessage()==null?"Connection failed":e.getMessage();
                runOnUiThread(() -> {
                    overall.setText("NOT CONNECTED — verification or authentication failed");
                    diagnosticOutput.setText("SSH check failed: "+message);
                });
            } finally {
                if(privateKey!=null) java.util.Arrays.fill(privateKey,(byte)0);
            }
        },"pradipweb-ssh-check").start();
    }

    private boolean hasEncryptedKey() {
        return getSharedPreferences(PREFS,0).contains("key_ciphertext");
    }
    private boolean hasPin() {
        return getSharedPreferences(PREFS,0).contains("host_fingerprint");
    }
    private SecretKey getOrCreateAesKey() throws Exception {
        KeyStore ks=KeyStore.getInstance("AndroidKeyStore"); ks.load(null);
        if (ks.containsAlias(KEY_ALIAS)) return (SecretKey)ks.getKey(KEY_ALIAS,null);
        KeyGenerator gen=KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES,"AndroidKeyStore");
        gen.init(new KeyGenParameterSpec.Builder(KEY_ALIAS,
            KeyProperties.PURPOSE_ENCRYPT|KeyProperties.PURPOSE_DECRYPT)
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setRandomizedEncryptionRequired(true).build());
        return gen.generateKey();
    }
    private void importKey(Uri uri) {
        try {
            ContentResolver cr=getContentResolver();
            ByteArrayOutputStream out=new ByteArrayOutputStream();
            try(InputStream in=cr.openInputStream(uri)) {
                if(in==null) throw new IllegalStateException("Cannot open selected file");
                byte[] buf=new byte[4096]; int n; int total=0;
                while((n=in.read(buf))!=-1) {
                    total+=n; if(total>65536) throw new IllegalArgumentException("Private key file exceeds 64 KB");
                    out.write(buf,0,n);
                }
            }
            byte[] plain=out.toByteArray();
            String keyText=new String(plain,StandardCharsets.UTF_8);
            if(!keyText.contains("PRIVATE KEY")) throw new IllegalArgumentException("Selected file does not look like a PEM private key");
            Cipher cipher=Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE,getOrCreateAesKey());
            byte[] encrypted=cipher.doFinal(plain);
            String iv=Base64.encodeToString(cipher.getIV(),Base64.NO_WRAP);
            String data=Base64.encodeToString(encrypted,Base64.NO_WRAP);
            getSharedPreferences(PREFS,0).edit().putString("key_iv",iv).putString("key_ciphertext",data).apply();
            java.util.Arrays.fill(plain,(byte)0);
            keyState.setText("Encrypted private key stored securely on this phone");
            Toast.makeText(this,"SSH key encrypted and stored",Toast.LENGTH_LONG).show();
        } catch(Exception e) {
            Toast.makeText(this,"Key import failed: "+e.getMessage(),Toast.LENGTH_LONG).show();
        }
    }
    private void savePin() {
        String value=fingerprintInput.getText().toString().trim();
        if(!value.matches("SHA256:[A-Za-z0-9+/]{20,}")) {
            fingerprintInput.setError("Enter a valid OpenSSH SHA256 fingerprint");
            return;
        }
        getSharedPreferences(PREFS,0).edit().putString("host_fingerprint",value).apply();
        pinState.setText("Trusted fingerprint saved on this phone");
        Toast.makeText(this,"Host fingerprint saved. It has not yet been tested.",Toast.LENGTH_LONG).show();
    }
    @Override protected void onActivityResult(int requestCode,int resultCode,Intent data) {
        super.onActivityResult(requestCode,resultCode,data);
        if(requestCode==PICK_KEY && resultCode==RESULT_OK && data!=null && data.getData()!=null) {
            getContentResolver().takePersistableUriPermission(data.getData(),Intent.FLAG_GRANT_READ_URI_PERMISSION);
            importKey(data.getData());
        }
    }
}