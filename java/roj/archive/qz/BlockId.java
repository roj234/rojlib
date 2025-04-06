package roj.archive.qz;

import roj.plugins.ci.annotation.CompileOnly;

@CompileOnly
interface BlockId {
     byte kEnd = 0;

     byte kHeader = 1;

     byte kArchiveProperties = 2;

     byte kAdditionalStreamsInfo = 3;
     byte kMainStreamsInfo = 4;
     byte kFilesInfo = 5;

     byte kPackInfo = 6;
     byte kUnPackInfo = 7;
     byte kSubStreamsInfo = 8;

     byte kSize = 9;
     byte kCRC = 10;

     byte kFolder = 11;

     byte kCodersUnPackSize = 12;
     byte kNumUnPackStream = 13;

     byte kEmptyStream = 14;
     byte kEmptyFile = 15;
     byte kAnti = 16;

     byte kName = 17;
     byte kCTime = 18;
     byte kATime = 19;
     byte kMTime = 20;
     byte kWinAttributes = 21;
     byte kComment = 22;

     byte kEncodedHeader = 23;

     byte kStartPos = 24;
     byte kDummy = 25;
}
