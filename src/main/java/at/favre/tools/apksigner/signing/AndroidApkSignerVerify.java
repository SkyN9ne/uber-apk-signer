package at.favre.tools.apksigner.signing;

import com.android.apksig.ApkVerifier;
import com.android.apksigner.ApkSignerTool;

import java.io.File;
import java.lang.reflect.Method;
import java.security.MessageDigest;
import java.security.PublicKey;
import java.security.cert.X509Certificate;
import java.security.interfaces.DSAKey;
import java.security.interfaces.DSAParams;
import java.security.interfaces.ECKey;
import java.security.interfaces.RSAKey;
import java.util.ArrayList;
import java.util.Date;
import java.util.Iterator;
import java.util.List;

public class AndroidApkSignerVerify {
    private static final char[] HEX_DIGITS = "0123456789abcdef".toCharArray();

    public Result verify(File apk, Integer minSdkVersion, Integer maxSdkVersion, boolean warningsTreatedAsErrors) throws Exception {
        StringBuilder logMsg = new StringBuilder();
        List<CertInfo> certInfoList = new ArrayList<>();
        int warningCount = 0;

        if (maxSdkVersion == null) {
            maxSdkVersion = Integer.MAX_VALUE;
        }

        if (minSdkVersion == null) {
            try {
                Method method = ApkSignerTool.class.getDeclaredMethod("getMinSdkVersionFromAndroidManifest", File.class);
                method.setAccessible(true);
                minSdkVersion = (Integer) method.invoke(null, apk);
            } catch (NoSuchMethodException e) {
                throw new IllegalStateException("Could not get private method from apkSigner lib", e);
            } catch (Exception e) {
                throw new IllegalStateException("Failed to deduce Min API Level from APK\'s AndroidManifest.xml. Use --min-sdk-version to override.", e);
            }
        }

        if (minSdkVersion > maxSdkVersion) {
            throw new IllegalStateException("Min API Level (" + minSdkVersion + ") > max API Level (" + maxSdkVersion + ")");
        }

        ApkVerifier.Result apkVerifierResult = (new ApkVerifier.Builder(apk)).setCheckedPlatformVersions(minSdkVersion, maxSdkVersion).build().verify();
        boolean verified = apkVerifierResult.isVerified();
        boolean warningsEncountered = false;
        Iterator iter;
        if (verified) {
            List signerCertificates = apkVerifierResult.getSignerCertificates();
            logMsg.append("Verifies\n");
            logMsg.append("Verified using v1 scheme (JAR signing): ").append(apkVerifierResult.isVerifiedUsingV1Scheme());
            logMsg.append("Verified using v2 scheme (APK Signature Scheme v2): ").append(apkVerifierResult.isVerifiedUsingV2Scheme());
            logMsg.append("Number of signers: ").append(signerCertificates.size());

            MessageDigest sha256Digners = MessageDigest.getInstance("SHA-256");
            MessageDigest sha1Digest = MessageDigest.getInstance("SHA-1");
            iter = signerCertificates.iterator();

            while (iter.hasNext()) {
                CertInfo certInfo = new CertInfo();

                X509Certificate x509Certificate = (X509Certificate) iter.next();
                byte[] encodedCert = x509Certificate.getEncoded();

                certInfo.subjectAndIssuerDn = "Subject: " + x509Certificate.getSubjectDN().toString() + " / Issuer: " + x509Certificate.getIssuerDN();
                certInfo.sigAlgo = x509Certificate.getSigAlgName();
                certInfo.certSha1 = encode(sha1Digest.digest(encodedCert));
                certInfo.certSha256 = encode(sha256Digners.digest(encodedCert));
                certInfo.expiry = x509Certificate.getNotAfter();
                certInfo.beginValidity = x509Certificate.getNotBefore();

                PublicKey publicKey = x509Certificate.getPublicKey();

                certInfo.pubAlgo = publicKey.getAlgorithm();
                int keySize = -1;
                if (publicKey instanceof RSAKey) {
                    keySize = ((RSAKey) publicKey).getModulus().bitLength();
                } else if (publicKey instanceof ECKey) {
                    keySize = ((ECKey) publicKey).getParams().getOrder().bitLength();
                } else if (publicKey instanceof DSAKey) {
                    DSAParams encodedKey = ((DSAKey) publicKey).getParams();
                    if (encodedKey != null) {
                        keySize = encodedKey.getP().bitLength();
                    }
                }

                certInfo.pubKeysize = keySize;
                byte[] pubKey = publicKey.getEncoded();
                certInfo.pubSha1 = encode(sha1Digest.digest(pubKey));
                certInfo.pubSha256 = encode(sha256Digners.digest(pubKey));
                certInfoList.add(certInfo);
            }
        } else {
            logMsg.append("DOES NOT VERIFY\n");
        }

        for (Object error : apkVerifierResult.getErrors()) {
            logMsg.append("ERROR: " + error).append("\n");
        }

        Iterator warningIter = apkVerifierResult.getWarnings().iterator();

        while (warningIter.hasNext()) {
            ApkVerifier.IssueWithParams var29 = (ApkVerifier.IssueWithParams) warningIter.next();
            warningsEncountered = true;
            warningCount++;
            logMsg.append("WARNING: ").append(var29).append("\n");
        }

        warningIter = apkVerifierResult.getV1SchemeSigners().iterator();

        String var32;
        ApkVerifier.IssueWithParams var33;
        while (warningIter.hasNext()) {
            ApkVerifier.Result.V1SchemeSignerInfo var30 = (ApkVerifier.Result.V1SchemeSignerInfo) warningIter.next();
            var32 = var30.getName();
            iter = var30.getErrors().iterator();

            while (iter.hasNext()) {
                var33 = (ApkVerifier.IssueWithParams) iter.next();
                logMsg.append("ERROR: JAR signer ").append(var32).append(": ").append(var33).append("\n");

            }

            iter = var30.getWarnings().iterator();

            while (iter.hasNext()) {
                var33 = (ApkVerifier.IssueWithParams) iter.next();
                warningsEncountered = true;
                warningCount++;
                logMsg.append("WARNING: JAR signer ").append(var32).append(": ").append(var33).append("\n");

            }
        }

        warningIter = apkVerifierResult.getV2SchemeSigners().iterator();

        while (warningIter.hasNext()) {
            ApkVerifier.Result.V2SchemeSignerInfo warningsInfo = (ApkVerifier.Result.V2SchemeSignerInfo) warningIter.next();
            var32 = "signer #" + (warningsInfo.getIndex() + 1);
            iter = warningsInfo.getErrors().iterator();

            while (iter.hasNext()) {
                var33 = (ApkVerifier.IssueWithParams) iter.next();
                logMsg.append("ERROR: APK Signature Scheme v2 ").append(var32).append(": ").append(var33).append("\n");
            }

            iter = warningsInfo.getWarnings().iterator();

            while (iter.hasNext()) {
                var33 = (ApkVerifier.IssueWithParams) iter.next();
                warningsEncountered = true;
                warningCount++;
                logMsg.append("WARNING: APK Signature Scheme v2 ").append(var32).append(": ").append(var33).append("\n");
            }
        }

        if (!verified || warningsTreatedAsErrors && warningsEncountered) {
            return new Result(false, warningCount, logMsg.toString(), apkVerifierResult.isVerifiedUsingV1Scheme(), apkVerifierResult.isVerifiedUsingV2Scheme(), certInfoList);
        }

        return new Result(true, warningCount, logMsg.toString(), apkVerifierResult.isVerifiedUsingV1Scheme(), apkVerifierResult.isVerifiedUsingV2Scheme(), certInfoList);
    }

    private static String encode(byte[] data, int offset, int length) {
        StringBuilder result = new StringBuilder(length * 2);

        for (int i = 0; i < length; ++i) {
            byte b = data[offset + i];
            result.append(HEX_DIGITS[b >>> 4 & 15]);
            result.append(HEX_DIGITS[b & 15]);
        }

        return result.toString();
    }

    private static String encode(byte[] data) {
        return encode(data, 0, data.length);
    }

    public static class Result {
        public final boolean verified;
        public final int warning;
        public final String log;
        public final boolean v1Schema;
        public final boolean v2Schema;
        public final List<CertInfo> certInfoList;

        public Result(boolean verified, int warning, String log, boolean v1Schema, boolean v2Schema, List<CertInfo> certInfoList) {
            this.verified = verified;
            this.warning = warning;
            this.log = log;
            this.v1Schema = v1Schema;
            this.v2Schema = v2Schema;
            this.certInfoList = certInfoList;
        }
    }

    public static class CertInfo {
        public String certSha1;
        public String certSha256;
        public String pubSha1;
        public String pubSha256;
        public String subjectAndIssuerDn;
        public String sigAlgo;
        public String pubAlgo;
        public int pubKeysize;
        public Date expiry;
        public Date beginValidity;
    }
}