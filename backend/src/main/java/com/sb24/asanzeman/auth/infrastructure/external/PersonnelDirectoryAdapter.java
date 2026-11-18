package com.sb24.asanzeman.auth.infrastructure.external;

import com.sb24.asanzeman.auth.domain.model.AccountName;
import com.sb24.asanzeman.auth.domain.model.Personnel;
import com.sb24.asanzeman.auth.domain.port.out.PersonnelDirectoryPort;
import com.sb24.asanzeman.integration.personnel.PersonnelInfoClient;
import java.util.Optional;
import org.springframework.stereotype.Component;

@Component
public class PersonnelDirectoryAdapter implements PersonnelDirectoryPort {

    private final PersonnelInfoClient client;

    public PersonnelDirectoryAdapter(PersonnelInfoClient client) {
        this.client = client;
    }

    @Override
    public Optional<Personnel> find(AccountName accountName) {
        return client.find(accountName.value()).map(info -> new Personnel(accountName, info.branchCode(),
                info.prsnCode(), info.personelUserName(), info.branchName(), info.position(), info.job()));
    }
}
