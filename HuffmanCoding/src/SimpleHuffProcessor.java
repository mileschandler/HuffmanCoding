/*  Student information for assignment:
 *
 *  On <MY|OUR> honor, Jacob Hungerford and Miles Chandler, this programming assignment is <MY|OUR> own work
 *  and <I|WE> have not provided this code to any other student.
 *
 *  Number of slip days used: 0
 *
 *  Student 1 Jacob Hungerford
 *  UTEID: jdh5468
 *  email address: JHungerford1516@utexas.edu
 *  Grader name: Anthony
 *
 *  Student 2
 *  UTEID: mac9325 Miles Chandler
 *  email address: miles.chandler@ichandler.net
 *
 */



import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

public class SimpleHuffProcessor implements IHuffProcessor {

    private IHuffViewer myViewer;
    private HuffmanTree huffmanTree;
    private int bitsSaved;
    private int headerFormat;
    private int[] frequencies;

    public int compress(InputStream in, OutputStream out, boolean force) throws IOException {
        int compressedBits = 0;
        if (bitsSaved > 0 || force) {
            BitInputStream bitIn = new BitInputStream(in);
            BitOutputStream bitOut = new BitOutputStream(out);
            //write the magic number
            bitOut.writeBits(BITS_PER_INT, MAGIC_NUMBER);
            compressedBits += BITS_PER_INT;
            //write the header format
            bitOut.writeBits(BITS_PER_INT, headerFormat);
            compressedBits += BITS_PER_INT;
            //write the storeCount header
            if (headerFormat == STORE_COUNTS){
                compressedBits += outputStdCount(bitOut, compressedBits);
            } else if (headerFormat == STORE_TREE) {
                //else write the stdTree header
                compressedBits += outputStdTree(bitOut, compressedBits);
            }
            compressedBits += outputData(bitOut, bitIn, compressedBits);
            //write the EOF
            String eof = huffmanTree.codeMap.get(PSEUDO_EOF);
            bitOut.writeBits(eof.length(), Integer.valueOf(eof, 2));
            compressedBits += eof.length();
            bitIn.close();
            bitOut.close();
        }
        return compressedBits;
    }

    private int outputStdCount(BitOutputStream bitOut, int bits){
        for (int i = 0; i < ALPH_SIZE; i++){
            bitOut.writeBits(BITS_PER_INT, frequencies[i]);
            bits += 32;
        }
        return bits;
    }

    private int outputStdTree(BitOutputStream bitOut, int bits){
        bitOut.writeBits(BITS_PER_INT, huffmanTree.getEncodeSize());
        bits += 32;
        for (TreeNode tNode : huffmanTree.getNodeList()){
            int tValue = tNode.getValue();
            //if the node is not a leaf
            if (tValue == -1) {// possible final constant
                //write a 0
                bitOut.writeBits(1, 0);
                bits++;
            }
            else {//if the node is a leaf
                //write a 1
                bitOut.writeBits(1, 1);
                bits++;
                //then write the value of the node
                bitOut.writeBits(BITS_PER_WORD + 1, tValue);
                bits += BITS_PER_WORD + 1;
            }

        }
        return bits;
    }

    private int outputData(BitOutputStream bitOut, BitInputStream bitIn, int bits) throws IOException{
        int inBits = 0;
        while ((inBits = bitIn.readBits(BITS_PER_WORD)) != -1){
            String code = huffmanTree.codeMap.get(inBits);
            bitOut.writeBits(code.length(), Integer.valueOf(code, 2));
            bits += code.length();
        }
        return bits;
    }

    public int preprocessCompress(InputStream in, int headerFormat) throws IOException {
        this.frequencies = new int[ALPH_SIZE + 1];
        this.headerFormat = headerFormat;
        frequencies[ALPH_SIZE] = 1;
        int preCompress = getFrequencies(new BitInputStream(in));
        huffmanTree = new HuffmanTree(frequencies);
        int compress = BITS_PER_INT * 2; //start at 32 because of the magic number is an int
        //plus another int for the header format
        //if the headerFormat is stre counts
        //increment by the alpsize times bits per int
        if (headerFormat == STORE_COUNTS){
            compress += ALPH_SIZE * BITS_PER_INT;
        }else if (headerFormat == STORE_TREE){
            //if the headerFormat is store tree
            //get number from huffman class
            compress += huffmanTree.getEncodeSize() + 32;
            //we add 32 bits to the total because store tree
            //requires us to store the size of the tree
        }
        compress += getCountOfData();
        bitsSaved = preCompress - compress;

        return bitsSaved;
    }

    private int getCountOfData(){
        int total = 0;
        for (int i = 0; i <= ALPH_SIZE; i++){
            if (huffmanTree.codeMap.get(i) != null)
                total += this.frequencies[i] * huffmanTree.codeMap.get(i).length();
        }
        return total;
    }



    public void setViewer(IHuffViewer viewer) {
        myViewer = viewer;
    }

    public int uncompress(InputStream in, OutputStream out) throws IOException {
        int uncompressedBits = 0;
        BitInputStream bitIn = new BitInputStream(in);
        int magicNumber = bitIn.readBits(BITS_PER_INT);
        if (magicNumber != MAGIC_NUMBER){
            myViewer.showError("Error reading compressed file. \n" +
                    "File did not start with the huff magic number.");
            bitIn.close();
            return -1;
        }
        headerFormat = bitIn.readBits(BITS_PER_INT);
        BitOutputStream bitOut = new BitOutputStream(out);
        HuffmanTree outTree;
        if (headerFormat == STORE_COUNTS){
            outTree = new HuffmanTree(inputStdCount(bitIn));
        }else if (headerFormat == STORE_TREE){
            outTree = outputStdTree(bitIn);
        }else{
            myViewer.showError("Error reading compressed file. \n" +
                    "Header format not recognized");
            bitIn.close();
            bitOut.close();
            return -1;
        }
        uncompressedBits += outTree.writeData(bitIn, bitOut);
        return uncompressedBits;
    }

    private HuffmanTree outputStdTree(BitInputStream bitIn) throws IOException{
        int size = bitIn.readBits(BITS_PER_INT);
        int[] currentSize = new int[1];
        TreeNode outRoot = makeTree(new TreeNode(-1, -1),
                bitIn, currentSize, size);
        return new HuffmanTree(outRoot);
    }

    private TreeNode makeTree(TreeNode n, BitInputStream bitIn, int[] currentSize,
                              int size)throws IOException{
        if (currentSize[0] < size){
            currentSize[0]++;
            int nodeType = bitIn.readBits(1);
            if (nodeType == 0){
                TreeNode newLeft = new TreeNode(-1, -1);
                n.setLeft(newLeft);
                newLeft.setLeft(makeTree(newLeft, bitIn, currentSize, size));
                newLeft.setRight(makeTree(newLeft, bitIn, currentSize, size));
                return newLeft;
            }else{
                int value = bitIn.readBits(BITS_PER_WORD + 1);
                currentSize[0] += BITS_PER_WORD + 1;
                return new TreeNode(value, -1);
            }
        }
        return null;
    }

    private void showString(String s){
        if(myViewer != null)
            myViewer.update(s);
    }
    
    private int getFrequencies(BitInputStream bits) throws IOException {
        int total = 0;
        int inbits;
        while ((inbits = bits.readBits(BITS_PER_WORD)) != -1) {
        	this.frequencies[inbits]++;
        	total += BITS_PER_WORD;
        }
        bits.close();
        return total;
    }

    private int[] inputStdCount(BitInputStream bitIn) throws IOException{
        //read the counts of the frequencies
        int[] inFreq = new int[ALPH_SIZE];
        for (int i = 0; i < inFreq.length; i++){
            int frequency = bitIn.readBits(BITS_PER_INT);
            inFreq[i] = frequency;
        }
        return inFreq;
    }
}
