package metopa.publication.web;

import metopa.publication.InstallmentType;

record CreateInstallmentRequest(
        InstallmentType type,
        int number,
        String title
) {
}