package com.org.meeple.infra.user.command.crypto

import com.org.meeple.infra.config.IdentityCryptoProperties
import org.springframework.stereotype.Component
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * CI 저장용 암호화기. 설정 키를 SHA-256으로 256bit AES 키로 파생하고 AES/GCM/NoPadding으로 암호화한다.
 * 결과는 Base64(iv(12) + ciphertext+tag). CI는 조회 로직에 쓰이지 않아 복호화는 제공하지 않는다(필요 시 확장).
 */
@Component
class CiCipher(properties: IdentityCryptoProperties) {

	private val keySpec: SecretKeySpec = SecretKeySpec(
		MessageDigest.getInstance("SHA-256").digest(properties.ciEncryptionKey.toByteArray(Charsets.UTF_8)),
		"AES",
	)
	private val random: SecureRandom = SecureRandom()

	fun encrypt(plain: String): String {
		val iv: ByteArray = ByteArray(IV_LENGTH).also { random.nextBytes(it) }
		val cipher: Cipher = Cipher.getInstance("AES/GCM/NoPadding")
		cipher.init(Cipher.ENCRYPT_MODE, keySpec, GCMParameterSpec(TAG_BITS, iv))
		val cipherText: ByteArray = cipher.doFinal(plain.toByteArray(Charsets.UTF_8))
		return Base64.getEncoder().encodeToString(iv + cipherText)
	}

	companion object {
		private const val IV_LENGTH: Int = 12
		private const val TAG_BITS: Int = 128
	}
}
