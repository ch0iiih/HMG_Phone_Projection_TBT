package hmgapi.mods.projection.carplay;
import java.util.Arrays;
public final class StockLaneHintsTest {
    static int checks;
    static void check(boolean value,String label){checks++;if(!value)throw new AssertionError(label);}
    static byte[] frame(int... icons){
        byte[] f=new byte[81];f[0]=(byte)241;f[1]=(byte)icons.length;
        for(int i=0;i<icons.length;i++){f[3+i]=1;f[19+i*3]=(byte)icons[i];}
        return f;
    }
    public static void main(String[] args){
        // Same-drive 04:32:15 stock geometry: three lanes, new pocket on right.
        byte[] stock=frame(1,1,1);stock[2]=1;
        byte[] phone=frame(1,1,3);phone[5]=3;
        byte[] saved=phone.clone();
        String hint=StockLaneHints.encode(1000,13000,140,stock);
        byte[] merged=StockLaneHints.merge(phone,hint,14000);
        check(merged[2]==1,"right pocket copied");
        for(int i=0;i<81;i++)if(i!=2)check(merged[i]==phone[i],"phone data retained");
        check(Arrays.equals(phone,saved),"original retained");
        check(StockLaneHints.merge(phone,hint,16001)==phone,"old distance rejected");
        check(StockLaneHints.merge(phone,StockLaneHints.encode(1000,32000,140,stock),32000)==phone,"old geometry rejected");
        check(StockLaneHints.merge(phone,StockLaneHints.encode(14000,14000,351,stock),14000)==phone,"far feature rejected");
        check(StockLaneHints.merge(phone,StockLaneHints.encode(14001,14000,140,stock),14000)==phone,"future rejected");
        byte[] different=frame(1,3);check(StockLaneHints.merge(different,hint,14000)==different,"different count rejected");
        byte[] conflict=frame(7,1,3);check(StockLaneHints.merge(conflict,hint,14000)==conflict,"different non-pocket arrows rejected");
        byte[] compound=frame(1,1,3);compound[26]=18;
        check(StockLaneHints.merge(compound,hint,14000)==compound,"different composite rejected");
        stock[2]=8;check(StockLaneHints.merge(phone,StockLaneHints.encode(14000,14000,140,stock),14000)==phone,"out-of-range pocket rejected");
        stock[2]=0;check(StockLaneHints.merge(phone,StockLaneHints.encode(14000,14000,140,stock),14000)==phone,"clear marker not resurrected");
        check(StockLaneHints.merge(phone,"bogus",14000)==phone,"bad input safe");
        System.out.println("StockLaneHints: "+checks+" checks passed");
    }
}
