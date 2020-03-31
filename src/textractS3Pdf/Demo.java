package textractS3Pdf;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import com.amazonaws.auth.AWSCredentials;
import com.amazonaws.auth.AWSStaticCredentialsProvider;
import com.amazonaws.auth.BasicAWSCredentials;
import com.amazonaws.services.textract.AmazonTextract;
import com.amazonaws.services.textract.AmazonTextractClientBuilder;
import com.amazonaws.services.textract.model.Block;
import com.amazonaws.services.textract.model.BoundingBox;
import com.amazonaws.services.textract.model.DocumentLocation;
import com.amazonaws.services.textract.model.GetDocumentTextDetectionRequest;
import com.amazonaws.services.textract.model.GetDocumentTextDetectionResult;
import com.amazonaws.services.textract.model.S3Object;
import com.amazonaws.services.textract.model.StartDocumentTextDetectionRequest;
import com.amazonaws.services.textract.model.StartDocumentTextDetectionResult;

public class Demo {
    private static final String documentInput = "SampleInput.pdf";
	private static final String bucketName = "textractimgtesting";
	private static final String documentOutput = "./documents/text.txt";
	private static final String awsCredentials = "./documents/credentials.txt";
	private static String aws_access_key_id;
	private static String aws_secret_access_key;
	public static void main(String args[]) {
        try {

        	System.out.println("Started Extracting  text from "+ documentInput+" Object from S3");
            List<ArrayList<TextLine>> linesInPages = extractText(bucketName, documentInput);
            File file = new File(documentOutput);
            if(!file.exists())
            	file.createNewFile();
            FileWriter writer = new FileWriter(file); 
            for(ArrayList<TextLine> line: linesInPages) {
            	for(TextLine linetext : line) {
            		writer.write(linetext.text + System.lineSeparator());
            	}
            }
            writer.close();
            System.out.println("Ended Extracting  text from "+documentInput+" Object from S3documentOutput\nSaved in plaintext "+documentOutput+" file");


        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    private static List<ArrayList<TextLine>> extractText(String bucketName, String documentName) throws InterruptedException {

    	com.amazonaws.client.builder.AwsClientBuilder.EndpointConfiguration endpoint = new com.amazonaws.client.builder
    																							.AwsClientBuilder.EndpointConfiguration(
    																									"https://textract.us-east-1.amazonaws.com", "us-east-1");
       
    	try {
			aws_access_key_id = Files.readAllLines(Paths.get(awsCredentials), Charset.defaultCharset()).get(1);
			aws_secret_access_key = Files.readAllLines(Paths.get(awsCredentials), Charset.defaultCharset()).get(2);
			aws_access_key_id = aws_access_key_id.substring((aws_access_key_id.indexOf("=")+1), aws_access_key_id.length());
			aws_secret_access_key = aws_secret_access_key.substring((aws_secret_access_key.indexOf("=")+1), aws_secret_access_key.length());
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
    	
    	AWSCredentials awsCredentials = new BasicAWSCredentials(aws_access_key_id, aws_secret_access_key);
    	
    	AmazonTextractClientBuilder amazonTextractClientBuilder = AmazonTextractClientBuilder.standard()
    																	.withEndpointConfiguration(endpoint)
    																	.withCredentials(new AWSStaticCredentialsProvider(awsCredentials));
       
        AmazonTextract client = amazonTextractClientBuilder.build();
       

        StartDocumentTextDetectionRequest req = new StartDocumentTextDetectionRequest()
                .withDocumentLocation(new DocumentLocation()
                        .withS3Object(new S3Object()
                                .withBucket(bucketName)
                                .withName(documentName)))
                .withJobTag("DetectingText");

        StartDocumentTextDetectionResult startDocumentTextDetectionResult = client.startDocumentTextDetection(req);

        GetDocumentTextDetectionRequest documentTextDetectionRequest = null;
        GetDocumentTextDetectionResult response = null;
        String jobStatus = "IN_PROGRESS";
        System.out.println("Waiting for job to complete...");
        while (jobStatus.equals("IN_PROGRESS")) {
            documentTextDetectionRequest = new GetDocumentTextDetectionRequest()
                    .withJobId(startDocumentTextDetectionResult.getJobId())
                    .withMaxResults(1);

            response = client.getDocumentTextDetection(documentTextDetectionRequest);
            jobStatus = response.getJobStatus();
        }

        int maxResults = 1000;
        String paginationToken = null;
        Boolean finished = false;

        List<ArrayList<TextLine>> pages = new ArrayList<ArrayList<TextLine>>();
        ArrayList<TextLine> page = null;
        BoundingBox boundingBox = null;

        while (finished == false) {
            documentTextDetectionRequest = new GetDocumentTextDetectionRequest()
                    .withJobId(startDocumentTextDetectionResult.getJobId())
                    .withMaxResults(maxResults)
                    .withNextToken(paginationToken);
            response = client.getDocumentTextDetection(documentTextDetectionRequest);

            //Show blocks information
            List<Block> blocks = response.getBlocks();
            for (Block block : blocks) {
                if (block.getBlockType().equals("PAGE")) {
                    page = new ArrayList<TextLine>();
                    pages.add(page);
                } else if (block.getBlockType().equals("LINE")) {
                    boundingBox = block.getGeometry().getBoundingBox();
                    page.add(new TextLine(boundingBox.getLeft(),
                            boundingBox.getTop(),
                            boundingBox.getWidth(),
                            boundingBox.getHeight(),
                            block.getText()));
                }
            }
            paginationToken = response.getNextToken();
            if (paginationToken == null)
                finished = true;
        }

        return pages;
    }
}
