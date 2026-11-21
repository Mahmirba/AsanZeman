package com.sb24.asanzeman.request.domain.port.out;

import com.sb24.asanzeman.request.domain.model.BranchData;
import java.util.List;

/** Where the branches come from: Yaghut {@code branches}, with the technical account (D35). */
public interface BranchSourcePort {

    /** @return every branch, all pages read */
    List<BranchData> fetchAll();
}
