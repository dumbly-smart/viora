package app.viora.security

import android.content.Context
import android.util.Base64

class EncryptedPreferencesBlobStore(
    context: Context,
    private val cipher: AndroidKeystoreCipher = AndroidKeystoreCipher(),
) {
    private val preferences = context.getSharedPreferences("viora_secure_local", Context.MODE_PRIVATE)

    @Synchronized
    fun put(key: String, plaintext: ByteArray) {
        val encoded = Base64.encodeToString(cipher.encrypt(plaintext), Base64.NO_WRAP)
        preferences.edit().putString(key, encoded).commit()
    }

    @Synchronized
    fun get(key: String): ByteArray? {
        val encoded = preferences.getString(key, null) ?: return null
        return runCatching { cipher.decrypt(Base64.decode(encoded, Base64.NO_WRAP)) }
            .getOrElse {
                preferences.edit().remove(key).commit()
                null
            }
    }

    @Synchronized
    fun remove(key: String) {
        preferences.edit().remove(key).commit()
    }

    @Synchronized
    fun clear() {
        preferences.edit().clear().commit()
        cipher.deleteKey()
    }
}
