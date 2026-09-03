import java.io.File;
import java.nio.file.Files;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.Signature;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.Base64;

/**
 * 为发布产物的 manifest 生成 Ed25519 签名侧车文件 (.sig)。
 * 用法: java scripts/SignUpdateArtifact.java <manifest-file> <sig-file>
 */
public class SignUpdateArtifact {
    public static void main(String[] args) throws Exception {
        if (args.length < 2) {
            System.err.println("Usage: java SignUpdateArtifact.java <manifest-file> <sig-file>");
            System.exit(1);
        }

        String keyPem = System.getenv("UPDATE_SIGNING_PRIVATE_KEY");
        if (keyPem == null || keyPem.isBlank()) {
            System.err.println("UPDATE_SIGNING_PRIVATE_KEY environment variable is missing");
            System.exit(1);
        }

        String clean = keyPem.replaceAll("-----[A-Z ]+-----", "").replaceAll("\\s+", "");
        byte[] keyBytes = Base64.getDecoder().decode(clean);
        PKCS8EncodedKeySpec spec = new PKCS8EncodedKeySpec(keyBytes);
        PrivateKey privateKey = KeyFactory.getInstance("Ed25519").generatePrivate(spec);

        File manifestFile = new File(args[0]);
        File sigFile = new File(args[1]);

        if (!manifestFile.exists()) {
            System.err.println("Manifest file not found: " + manifestFile.getAbsolutePath());
            System.exit(1);
        }

        byte[] data = Files.readAllBytes(manifestFile.toPath());
        Signature sig = Signature.getInstance("Ed25519");
        sig.initSign(privateKey);
        sig.update(data);
        byte[] signature = sig.sign();

        Files.write(sigFile.toPath(), signature);
        System.out.println("Generated signature: " + sigFile.getAbsolutePath() + " (" + signature.length + " bytes)");
    }
}
