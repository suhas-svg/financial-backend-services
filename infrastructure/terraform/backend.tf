terraform {
  # Shared remote backend with state locking.
  # Initialize using: terraform init -backend-config=backend-prod.hcl
  backend "s3" {
    bucket         = "finance-platform-terraform-state"
    key            = "core-services/prod/terraform.tfstate"
    region         = "us-east-1"
    encrypt        = true
    dynamodb_table = "finance-platform-terraform-locks"
  }
}
