package metopa.publication.web;

import metopa.publication.InstallmentType;

record UpdateInstallmentRequest(
        InstallmentType type,
        int number,
        String title
) {
}