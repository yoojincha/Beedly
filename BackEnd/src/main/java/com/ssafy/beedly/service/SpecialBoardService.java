package com.ssafy.beedly.service;

import com.amazonaws.services.s3.AmazonS3Client;
import com.amazonaws.services.s3.model.CannedAccessControlList;
import com.amazonaws.services.s3.model.ObjectMetadata;
import com.amazonaws.services.s3.model.PutObjectRequest;
import com.ssafy.beedly.common.exception.NotFoundException;
import com.ssafy.beedly.common.exception.NotMatchException;
import com.ssafy.beedly.domain.Category;
import com.ssafy.beedly.domain.SpecialBoard;
import com.ssafy.beedly.domain.User;
import com.ssafy.beedly.domain.type.YN;
import com.ssafy.beedly.dto.special.board.request.CreateSpecialBoardRequest;
import com.ssafy.beedly.dto.special.board.response.SpecialBoardResponse;
import com.ssafy.beedly.dto.special.board.response.SpecialBoardSimpleResponse;
import com.ssafy.beedly.repository.CategoryRepository;
import com.ssafy.beedly.repository.SpecialAuctionRepository;
import com.ssafy.beedly.repository.SpecialBoardRepository;
import com.ssafy.beedly.repository.SpecialProductRepository;
import com.ssafy.beedly.repository.query.SpecialBoardQueryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

import static com.ssafy.beedly.common.exception.NotFoundException.CATEGORY_NOT_FOUND;
import static com.ssafy.beedly.common.exception.NotFoundException.SPECIAL_BOARD_NOT_FOUND;
import static com.ssafy.beedly.common.exception.NotMatchException.CONTENT_TYPE_NOT_MATCH;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SpecialBoardService {

    @Value("${cloud.aws.s3.bucket}")
    private String bucket;

    private final SpecialBoardRepository specialBoardRepository;
    private final SpecialBoardQueryRepository specialBoardQueryRepository;
    private final AmazonS3Client amazonS3Client;
    private final SpecialProductRepository specialProductRepository;
    private final SpecialAuctionRepository specialAuctionRepository;

    // ????????? ??????
    @Transactional
    public Long createPost(User user, CreateSpecialBoardRequest request, MultipartFile image) {
        String imageUrl = uploadImageS3(image);
        return specialBoardRepository.save(SpecialBoard.createSpecialBoard(user, request, imageUrl)).getId();
    }

    // ????????? ????????????
    public SpecialBoardResponse searchSpecialBoard(Long boardId) {
        return new SpecialBoardResponse(specialBoardQueryRepository.findByIdWithProducts(boardId));
    }

    // ?????? ????????? ????????? ????????? ??????
    public List<SpecialBoardSimpleResponse> searchWaitingSpecialBoards() {
        return specialBoardRepository.findWaitingSpecialBoardOrderByStartTimeAsc(LocalDateTime.now().minusHours(9))
                .stream().map(specialBoard -> new SpecialBoardSimpleResponse(specialBoard))
                .collect(Collectors.toList());
    }

    // ????????? ?????? ??????
    @Transactional
    public void updateSpecialBoard(Long boardId, CreateSpecialBoardRequest request, MultipartFile image) {
        SpecialBoard findSpecialBoard = specialBoardRepository.findById(boardId)
                .orElseThrow(() -> new NotFoundException(SPECIAL_BOARD_NOT_FOUND));

        findSpecialBoard.updateSpecialBoard(request);

        if (image != null) {
            String imageUrl = uploadImageS3(image);
            findSpecialBoard.updateImage(imageUrl);
        }
    }

    // ????????? ??????
    @Transactional
    public void deleteSpecialBoard(Long boardId) {
        SpecialBoard findSpecialBoard = specialBoardRepository.findById(boardId)
                .orElseThrow(() -> new NotFoundException(SPECIAL_BOARD_NOT_FOUND));

        findSpecialBoard.deleteSpecialBoard();

        specialProductRepository.softDeleteProductByProductId(findSpecialBoard.getId(), YN.Y);
    }

    // ?????? ???????????? ????????? ????????? ??????
    public List<SpecialBoardSimpleResponse> searchOnAirSpecialBoards() {
        return specialAuctionRepository.findOnAirList().stream().map(specialAuction -> new SpecialBoardSimpleResponse(specialAuction))
                .collect(Collectors.toList());
    }

    public String uploadImageS3(MultipartFile image) {
        String imageUrl = null;

        if(image != null) {
            if (!image.getContentType().startsWith("image")) {
                throw new NotMatchException(CONTENT_TYPE_NOT_MATCH);
            }

            ObjectMetadata objectMetadata = new ObjectMetadata();
            objectMetadata.setContentLength(image.getSize());
            objectMetadata.setContentType(image.getContentType());
            String storeName = UUID.randomUUID().toString();

            try {
                amazonS3Client.putObject((new PutObjectRequest(bucket, storeName, image.getInputStream(), objectMetadata)
                        .withCannedAcl(CannedAccessControlList.PublicRead)));

                // ????????? url ????????????
                imageUrl = amazonS3Client.getUrl(bucket, storeName).toString();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        return imageUrl;
    }
}
