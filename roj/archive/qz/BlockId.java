package roj.archive.qz;

interface BlockId {
     byte iEnd = 0;
     byte iHeader = 1;
     byte iProp = 2;
     byte iMoreStream = 3;
     byte iArchiveInfo = 4;
     byte iFilesInfo = 5;
     byte iStreamInfo = 6;
     byte iWordBlockInfo = 7;
     byte iBlockFileMap = 8;
     byte iSize = 9;
     byte iCRC32 = 10;
     byte iWordBlock = 11;
     byte iWordBlockSizes = 12;
     byte iFileCounts = 13;
     byte iEmpty = 14;
     byte iEmptyFile = 15;
     byte iDeleteFile = 16;
     byte iFileName = 17;
     byte iCTime = 18;
     byte iATime = 19;
     byte iMTime = 20;
     byte iAttribute = 21;
     byte iComment = 22;
     byte iPackedHeader = 23;
     byte iStartPos = 24;
     byte iDummy = 25;
}
