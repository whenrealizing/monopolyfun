package com.monopolyfun.modules.workthread.service;

import com.monopolyfun.modules.workthread.domain.ProjectRevenueAddressEntity;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import org.web3j.crypto.Credentials;
import org.web3j.crypto.Hash;
import org.web3j.crypto.Sign;
import org.web3j.utils.Numeric;

import java.io.ByteArrayOutputStream;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.HexFormat;

@Service
public class RevenueClaimAuthorizationService {
    private static final byte CLAIM_SEPARATOR = 0x1f;

    private final String signerPrivateKey;

    public RevenueClaimAuthorizationService(Environment environment) {
        this.signerPrivateKey = readString(environment,
                "monopolyfun.revenue.claim-signer-private-key",
                "MONOPOLYFUN_REVENUE_CLAIM_SIGNER_PRIVATE_KEY",
                "");
    }

    public String sign(ProjectRevenueAddressEntity revenueAddress, String period, String accountId, String recipient, int amountMinor) {
        String privateKey = normalizePrivateKey(signerPrivateKey);
        if (privateKey == null) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Revenue claim signer private key required");
        }
        byte[] digest = authorizationDigest(revenueAddress.contractAddress(), revenueAddress.chainId(), period, accountId, recipient, amountMinor);
        Sign.SignatureData signature = Sign.signPrefixedMessage(digest, Credentials.create(privateKey).getEcKeyPair());
        byte[] encoded = new byte[65];
        System.arraycopy(padded(signature.getR(), 32), 0, encoded, 0, 32);
        System.arraycopy(padded(signature.getS(), 32), 0, encoded, 32, 32);
        encoded[64] = signature.getV()[0];
        return "0x" + HexFormat.of().formatHex(encoded);
    }

    static byte[] authorizationDigest(String contractAddress, String chainId, String period, String accountId, String recipient, int amountMinor) {
        return Hash.sha3(authorizationMessage(contractAddress, chainId, period, accountId, recipient, amountMinor));
    }

    private static byte[] authorizationMessage(String contractAddress, String chainId, String period, String accountId, String recipient, int amountMinor) {
        try {
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            output.write(addressBytes(contractAddress));
            output.write(uint256(chainIdNumber(chainId)));
            output.write(CLAIM_SEPARATOR);
            output.write(required(period, "period").getBytes(StandardCharsets.UTF_8));
            output.write(CLAIM_SEPARATOR);
            output.write(required(accountId, "accountId").getBytes(StandardCharsets.UTF_8));
            output.write(CLAIM_SEPARATOR);
            output.write(addressBytes(recipient));
            output.write(uint256(BigInteger.valueOf(amountMinor)));
            return output.toByteArray();
        } catch (ResponseStatusException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Revenue claim authorization payload is invalid", exception);
        }
    }

    private static byte[] addressBytes(String address) {
        String value = Numeric.cleanHexPrefix(required(address, "address"));
        if (!value.matches("[a-fA-F0-9]{40}")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Revenue claim address must be an EVM address");
        }
        return HexFormat.of().parseHex(value.toLowerCase());
    }

    private static byte[] uint256(BigInteger value) {
        if (value.signum() < 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Revenue claim amount must be non-negative");
        }
        byte[] raw = value.toByteArray();
        if (raw.length > 32 && raw[0] == 0) {
            raw = Arrays.copyOfRange(raw, 1, raw.length);
        }
        if (raw.length > 32) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Revenue claim uint256 value is too large");
        }
        byte[] padded = new byte[32];
        System.arraycopy(raw, 0, padded, 32 - raw.length, raw.length);
        return padded;
    }

    private static BigInteger chainIdNumber(String chainId) {
        String value = required(chainId, "chainId").trim();
        if (value.startsWith("eip155:")) {
            value = value.substring("eip155:".length());
        }
        if (!value.matches("\\d+")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Revenue claim chainId must be decimal or eip155 decimal");
        }
        return new BigInteger(value);
    }

    private static byte[] padded(byte[] value, int length) {
        if (value.length == length) {
            return value;
        }
        byte[] padded = new byte[length];
        System.arraycopy(value, Math.max(0, value.length - length), padded, Math.max(0, length - value.length), Math.min(value.length, length));
        return padded;
    }

    private static String normalizePrivateKey(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String normalized = Numeric.cleanHexPrefix(value.trim());
        if (!normalized.matches("[a-fA-F0-9]{64}")) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Revenue claim signer private key is invalid");
        }
        return normalized;
    }

    private static String required(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Revenue claim " + field + " is required");
        }
        return value.trim();
    }

    private static String readString(Environment environment, String property, String env, String defaultValue) {
        String value = environment.getProperty(property);
        if (value == null || value.isBlank()) {
            value = environment.getProperty(env);
        }
        return value == null || value.isBlank() ? defaultValue : value.trim();
    }
}
