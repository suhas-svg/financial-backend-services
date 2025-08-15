# Terraform Backend Configuration
# This file configures remote state management for different environments

# Development environment uses local state for simplicity
# Staging and Production should use remote backends in real scenarios

# Example remote backend configurations (commented out for local development)

# For AWS S3 backend:
# terraform {
#   backend "s3" {
#     bucket         = "account-service-terraform-state"
#     key            = "environments/${var.environment}/terraform.tfstate"
#     region         = "us-west-2"
#     encrypt        = true
#     dynamodb_table = "terraform-state-lock"
#   }
# }

# For Azure Storage backend:
# terraform {
#   backend "azurerm" {
#     resource_group_name  = "terraform-state-rg"
#     storage_account_name = "accountservicetfstate"
#     container_name       = "tfstate"
#     key                  = "environments/${var.environment}/terraform.tfstate"
#   }
# }

# For Google Cloud Storage backend:
# terraform {
#   backend "gcs" {
#     bucket = "account-service-terraform-state"
#     prefix = "environments/${var.environment}"
#   }
# }

# For Terraform Cloud backend:
# terraform {
#   backend "remote" {
#     organization = "your-organization"
#     workspaces {
#       prefix = "account-service-"
#     }
#   }
# }

# Local backend configuration for development
terraform {
  backend "local" {
    # State file will be stored locally in each environment directory
    # This is suitable for development and testing
  }
}

# State locking configuration (for remote backends)
# When using remote backends, ensure state locking is enabled to prevent
# concurrent modifications that could corrupt the state file

# Example DynamoDB table for AWS S3 backend state locking:
# resource "aws_dynamodb_table" "terraform_state_lock" {
#   name           = "terraform-state-lock"
#   billing_mode   = "PAY_PER_REQUEST"
#   hash_key       = "LockID"
#
#   attribute {
#     name = "LockID"
#     type = "S"
#   }
#
#   tags = {
#     Name        = "Terraform State Lock Table"
#     Environment = "shared"
#   }
# }