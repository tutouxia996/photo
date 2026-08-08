import com.drew.imaging.ImageMetadataReader;
import com.drew.metadata.Metadata;
import com.drew.metadata.exif.ExifSubIFDDirectory;
import com.drew.metadata.exif.ExifIFD0Directory;
import net.coobird.thumbnailator.Thumbnails;
import java.io.File;

public class MvimgTest {
  public static void main(String[] args) throws Exception {
    File f = new File(args[0]);
    System.out.println("file=" + f + " size=" + f.length());
    try {
      Metadata meta = ImageMetadataReader.readMetadata(f);
      ExifSubIFDDirectory sub = meta.getFirstDirectoryOfType(ExifSubIFDDirectory.class);
      if (sub != null) {
        System.out.println("FNUMBER=" + sub.getDescription(ExifSubIFDDirectory.TAG_FNUMBER));
        System.out.println("EXPOSURE=" + sub.getDescription(ExifSubIFDDirectory.TAG_EXPOSURE_TIME));
        System.out.println("FOCAL=" + sub.getDescription(ExifSubIFDDirectory.TAG_FOCAL_LENGTH));
        System.out.println("LENS=" + sub.getDescription(ExifSubIFDDirectory.TAG_LENS_MODEL));
        System.out.println("ISO=" + sub.getInteger(ExifSubIFDDirectory.TAG_ISO_EQUIVALENT));
      }
      ExifIFD0Directory ifd0 = meta.getFirstDirectoryOfType(ExifIFD0Directory.class);
      if (ifd0 != null) {
        System.out.println("MAKE=" + ifd0.getDescription(ExifIFD0Directory.TAG_MAKE));
        System.out.println("MODEL=" + ifd0.getDescription(ExifIFD0Directory.TAG_MODEL));
      }
    } catch (Throwable t) {
      System.out.println("EXIF FAIL: " + t);
      t.printStackTrace(System.out);
    }
    File out = new File("d:/work/学习/相册网站/tmp_mvimg_test/out.jpg");
    try {
      Thumbnails.of(f).width(320).keepAspectRatio(true).toFile(out);
      System.out.println("THUMB OK " + out.length());
    } catch (Throwable t) {
      System.out.println("THUMB FAIL: " + t);
      t.printStackTrace(System.out);
    }
  }
}
