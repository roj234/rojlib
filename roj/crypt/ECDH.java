package roj.crypt;

import roj.util.ByteList;

import java.math.BigInteger;

/**
 * y^2 = x^3 + ax + b <br/>
 * 假设爱丽丝与鲍勃需要建立共享密钥，但他们之间唯一的信道可能被第三方伊夫窃听，此时可以使用椭圆曲线密码学。
 * 首先，需要事先提前约定域参数（质数域 { F_{p}} { F_{p}}时为 { (p,a,b,G,n,h)} { (p,a,b,G,n,h)}，
 * 二元域 { F_{2}} F_{{2}}时为 { (m,f(x),a,b,G,n,h)} { (m,f(x),a,b,G,n,h)}），
 * 它是公开信息，定义了所使用的椭圆曲线；然后，双方准备符合条件的密钥
 * （在区间 { [1,n-1]} { [1,n-1]}随机一个整数作为私钥 { d} d，
 * 并与基点 { G} G相乘得到点 { Q=dG} { Q=dG}，即公钥），
 * 此时爱丽丝的密钥为 { (d_{A},Q_{A})} { (d_{A},Q_{A})}，
 * 鲍勃的密钥为 { (d_{B},Q_{B})} { (d_{B},Q_{B})}；
 * 接着，双方将自己的公钥 { Q_{A}} { Q_{A}}或 { Q_{B}} { Q_{B}}发送给对方；
 *
 * 爱丽丝计算点 { (x_{k},y_{k})=d_{A}Q_{B}} { (x_{k},y_{k})=d_{A}Q_{B}}，
 * 鲍勃计算点 { (x_{k},y_{k})=d_{B}Q_{A}} { (x_{k},y_{k})=d_{B}Q_{A}}，
 * 这就得到了双方的共享秘密 { x_{k}} x_k（即该点的x坐标）。
 * 由于 { d_{A}Q_{B}=d_{A}d_{B}G=d_{B}d_{A}G=d_{B}Q_{A}} { d_{A}Q_{B}=d_{A}d_{B}G=d_{B}d_{A}G=d_{B}Q_{A}}，
 * 因此双方得到的 { x_{k}} x_k是相等的。
 * 在实际应用中，常使用 { x_{k}} x_k和其他相关参数作为一个密钥衍生函数的输入，密钥为其输出。
 *
 * 在这个过程中，伊夫知道椭圆曲线的域参数，但爱丽丝只透露了她的公钥 { Q_{A}} { Q_{A}}，伊
 * 夫无法获得她的私钥 { d_{A}} { d_{A}}，除非伊夫能够解决椭圆曲线上的离散对数问题，这个问题被认为是困难的。
 * 同理，鲍勃的私钥也是安全的。
 * 若伊夫要计算出双方的共享秘密 { x_{k}} x_k，就需要求解迪菲-赫尔曼问题，而计算离散对数是此问题的已知最优解法，伊夫无法用其他方式直接解出共享秘密。
 * @author solo6975
 * @since 2022/2/12 15:31
 */
public class ECDH {
    static final class ECDHPoint {
        BigInteger x, y;

        public ECDHPoint() {}
        public ECDHPoint(BigInteger x, BigInteger y) {
            this.x = x;
            this.y = y;
        }

        public ECDHPoint mul(BigInteger privateKey) {
            return new ECDHPoint(x.multiply(privateKey), y.multiply(privateKey));
        }
    }

    public static void main(String[] args) {
        DH client = new DH();
        ByteList my = new ByteList();

        client.write1(my);

        DH server = new DH();
        BigInteger bi1 = server.read1(my);
        System.out.println(bi1);

        my.clear();
        server.write2(my);

        BigInteger bi2 = client.read2(my);
        System.out.println(bi2);
    }

    private final BigInteger a, b;
    private final ECDHPoint G;

    // y^2 = x^3 + ax + b
    public ECDH(BigInteger a, BigInteger b) {
        this.a = a;
        this.b = b;
        this.G = new ECDHPoint();
        G.x = new BigInteger("214123542352344232572348952763982");
        G.y = getY(G.x);
    }

    private BigInteger getY(BigInteger x) {
        return x.pow(3).add(a.multiply(x)).add(b);
    }

    public ECDHPoint getPublicKey(BigInteger privateKey) {
        return G.mul(privateKey);
    }

    public BigInteger getCommonValue(BigInteger myPrivateKey, BigInteger otherPublicKey) {
        return myPrivateKey.multiply(otherPublicKey);
    }
}
