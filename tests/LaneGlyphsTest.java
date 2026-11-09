package hmgapi.mods.projection.carplay;
import java.util.Arrays;
public final class LaneGlyphsTest {
    static int checks;
    static void check(boolean ok, String label) {
        checks++;
        if (!ok) throw new AssertionError(label);
    }
    static void expect(int status, int[] angles, int[] selected, int... wanted) {
        int[] actual = LaneGlyphs.encode(status, angles, selected);
        check(Arrays.equals(wanted, actual), Arrays.toString(actual) + " != " + Arrays.toString(wanted));
    }
    public static void main(String[] args) {
        // Actual photographed input: unavailable straight/left, usable right.
        expect(0, new int[]{0,-90}, null, 15,9,12,0,0,0);
        expect(1, new int[]{90}, new int[]{90}, 1,3,0,0,1,0);
        expect(1, new int[]{0,-90}, new int[]{-90}, 7,9,12,0,1,0);
        expect(2, new int[]{0,-90}, new int[]{-90}, 11,9,12,0,1,1);
        expect(1, new int[]{0,90}, new int[]{0}, 13,16,18,0,1,0);
        expect(2, new int[]{-90,0,90}, new int[]{0}, 62,47,53,49,1,1);
        expect(0, new int[]{90}, new int[]{90}, 3,3,0,0,0,0);
        expect(1, new int[]{0,0,-90}, null, 5,9,12,0,1,0);
        expect(-1, new int[]{0}, null, 3,1,0,0,0,0);
        expect(1, null, null, 0,0,0,0,0,0);
        // IMG_4094: exact 04:43:04 input must use the native left family.
        expect(0, new int[]{-180,-90}, null, 15,14,10,0,0,0);
        expect(1, new int[]{-180,-90}, new int[]{-90}, 13,14,10,0,1,0);
        expect(2, new int[]{-180,-90}, new int[]{-180}, 11,14,10,0,1,1);
        // All one/two/three-direction combinations retain all parts and encode
        // each status independently. Unknown status never implies passability.
        int[] angles={0,45,90,135,180,-135,-90,-45};
        for (int mask=1;mask<256;mask++) {
            int n=Integer.bitCount(mask);
            if(n>3)continue;
            int[] a=new int[n];int j=0;
            for(int i=0;i<8;i++)if((mask&(1<<i))!=0)a[j++]=angles[i];
            for(int status=0;status<3;status++) {
                int[] r=LaneGlyphs.encode(status,a,null);
                for(int i=0;i<n;i++) {
                    check(r[i+1]>0 && r[i+1]<=57,"glyph range");
                    check(((r[0]>>(2*i))&3)==(status==0?3:status==1?1:2),"part color");
                }
                check((r[0]>>(2*n))==0,"unused color bits");
            }
        }
        System.out.println("LaneGlyphs: "+checks+" checks passed");
    }
}
